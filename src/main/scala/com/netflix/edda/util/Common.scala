package com.netflix.edda.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import com.netflix.config.ConcurrentCompositeConfiguration
import com.netflix.config.DynamicConfiguration
import com.netflix.config.DynamicProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.config.DynamicStringProperty
import com.netflix.config.FixedDelayPollingScheduler
import com.netflix.config.sources.URLConfigurationSource
import com.netflix.edda.actors.StateMachine
import com.netflix.edda.actors.StateMachine.Handler
import com.netflix.edda.datastores.Datastore
import com.netflix.edda.records.Record
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.IOUtils
import org.codehaus.jackson.JsonEncoding.UTF8
import org.codehaus.jackson.JsonGenerator
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.MappingJsonFactory
import org.codehaus.jackson.util.DefaultPrettyPrinter
import org.joda.time.DateTime

import scala.actors.DaemonActor
import scala.actors.IScheduler
import scala.actors.Scheduler

/** singleton object for various helper functions */
object Common extends StrictLogging {

  private lazy val factory = new MappingJsonFactory

  private lazy val dateFormat = {
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    formatter
  }

  /** object to help retry critical operations.   It catches
    * any exceptions, logs them, and retrys the operation.
    *
    * {{{
    * val state = RETRY { initState }
    * }}}
    *
    */
  object RETRY {
    final def apply[T](action: => T): T = {
      {
        try {
          Some(action)
        } catch {
          case e: Exception =>
            logger.error(s"caught retryable exception: ${e.getMessage}", e)
            None
        }
      } match {
        case Some(t) => t.asInstanceOf[T]
        case None    => RETRY.apply(action)
      }
    }

    final def apply[T](n: Int)(action: => T): T = {
      {
        try {
          action
        } catch {
          case e: Exception =>
            logger.error(s"caught retryable exception [$n]: ${e.getMessage}", e)
            e
        }
      } match {
        case _: Exception if n > 1 =>
          Thread.sleep(100)
          RETRY.apply(n - 1)(action)

        case e: Exception =>
          throw e

        case t =>
          t.asInstanceOf[T]
      }
    }
  }

  trait ActorExceptionHandler extends DaemonActor {
    var handlers: Handler = Map()

    /** add a partial function to allow for specific exception
      * handling when needed
      *
      * @param pf PartialFunction to handle exception types
      */
    def addExceptionHandler(pf: Handler): ActorExceptionHandler = {
      handlers = pf orElse handlers
      this
    }
  }

  /** class used to assist logging and allow for abstracted exception handling
    * for simple actors
    *
    * @param name name of actor that is seen when logging
    * @param body closure run as the actors "act" routine
    */
  def namedActor(name: String)(body: => Unit): ActorExceptionHandler = {
    val a: ActorExceptionHandler = new ActorExceptionHandler {
      override def toString: String = name
      override def act(): Unit = body

      /** setup exceptionHandler to use the custom handlers modified with addExceptionHandler */
      override def exceptionHandler: StateMachine.Handler = handlers

      // dont use parentScheduler use global pool
      override final val scheduler: IScheduler = Scheduler
    }

    a.start()
    a
  }

  /** Hierarchical property lookup.
    *
    * if we have:
    *
    *   prefix       = "edda.collection"
    *   propName     = "enabled"
    *   nameContext  = "test.us-east-1.aws.addresses"
    *
    * look for the following, in order:
    *
    *   edda.collection.test.us-east-1.aws.addresses.enabled
    *   edda.collection.test.us-east-1.aws.enabled
    *   edda.collection.us-east-1.aws.addresses.enabled
    *   edda.collection.test.us-east-1.enabled
    *   edda.collection.us-east-1.aws.enabled
    *   edda.collection.aws.addresses.enabled
    *   edda.collection.test.enabled
    *   edda.collection.us-east-1.enabled
    *   edda.collection.aws.enabled
    *   edda.collection.addresses.enabled
    *   edda.collection.enabled
    *
    * else:
    *
    *   return default
    *
    * @param prefix root prefix, generally "edda.something"
    * @param propName property name, such as "enabled"
    * @param nameContext property names to search though
    * @param defaultValue the default value to return, if no matching properties are found
    * @return the best matching property value
    */
  def getProperty(
    config: Config,
    prefix: String,
    propName: String,
    nameContext: String,
    defaultValue: String
  ): String = {

    val requestedProp = {
      if (nameContext.isEmpty)
        s"$prefix.$propName"
      else
        s"$prefix.$nameContext.$propName"
    }

    val parts = nameContext.split('.')

    Range(1, parts.length + 1).reverse.flatMap(
      x => parts.sliding(x).map(s"$prefix." + _.mkString(".") + s".$propName")
    ) collectFirst {
      case prop if config.hasPath(prop) =>
        val v = config.getString(prop)
        logger.debug(s"using property $prop=$v for $requestedProp")
        v
    } match {
      case Some(v) =>
        v
      case None =>
        logger.debug(s"using default value $defaultValue for $requestedProp")
        defaultValue
    }
  }

  /** convert list of Any to list of AnyRef.  This is useful for slf4j printf style formatting:
    *
    * {{{
    * logger.info("stuff {} {} {} {}", toObjects(1, 1.2, true, "string"))
    * }}}
    *
    * @param args list of items to massage into list of AnyRef
    */
  def toObjects(args: Any*): Array[AnyRef] = {
    args
      .map(
        arg =>
          arg match {
            case null                => null
            case v: Char             => v.asInstanceOf[java.lang.Character]
            case v: Byte             => v.asInstanceOf[java.lang.Byte]
            case v: Short            => v.asInstanceOf[java.lang.Short]
            case v: Int              => v.asInstanceOf[java.lang.Integer]
            case v: Long             => v.asInstanceOf[java.lang.Long]
            case v: Float            => v.asInstanceOf[java.lang.Float]
            case v: Double           => v.asInstanceOf[java.lang.Double]
            case v: Boolean          => v.asInstanceOf[java.lang.Boolean]
            case (v: Any, f: String) => f.format(v)
            case v: AnyRef           => v
        }
      )
      .toArray[AnyRef]
  }

  /** convert an object to a json string, using the pretty printer formatter */
  def toPrettyJson(obj: Any): String = {
    val baos = new ByteArrayOutputStream()
    val gen = factory.createJsonGenerator(baos, UTF8)
    val dpp = new DefaultPrettyPrinter
    dpp.indentArraysWith(new DefaultPrettyPrinter.Lf2SpacesIndenter)
    gen.setPrettyPrinter(dpp)
    writeJson(gen, obj, dateFormatter)
    gen.close()
    baos.toString
  }

  /** convert an object to a json string */
  def toJson(obj: Any, formatter: Any => Any = (x: Any) => x): String = {
    val baos = new ByteArrayOutputStream()
    val gen = factory.createJsonGenerator(baos, UTF8)
    writeJson(gen, obj, formatter)
    gen.close()
    baos.toString
  }

  /** given an JsonGenerator, write object to generator.  Apply
    * formatter to possible translate data (ie convert millisecond
    * timetamps to humanreadable time strings)
    *
    * @param gen JsonGenerator to write to
    * @param obj object to convert to json
    * @param fmt arbitrary object translator
    */
  def writeJson(gen: JsonGenerator, obj: Any, fmt: Any => Any = (x: Any) => x) {
    fmt(obj) match {
      case v: Boolean  => gen.writeBoolean(v)
      case v: Byte     => gen.writeNumber(v)
      case v: Short    => gen.writeNumber(v)
      case v: Int      => gen.writeNumber(v)
      case v: Long     => gen.writeNumber(v)
      case v: Float    => gen.writeNumber(v)
      case v: Double   => gen.writeNumber(v)
      case v: Char     => gen.writeString("" + v)
      case v: String   => gen.writeString(v)
      case v: Date     => gen.writeNumber(v.getTime)
      case v: Record   => writeJson(gen, v.toMap, fmt)
      case v: DateTime => gen.writeNumber(v.getMillis)
      case v: Map[_, _] =>
        gen.writeStartObject()
        v.toSeq
          .sortBy(_._1.asInstanceOf[String])
          .foreach(pair => {
            gen.writeFieldName(pair._1.toString)
            writeJson(gen, pair._2, fmt)
          })
        gen.writeEndObject()
      case v: Seq[_] =>
        gen.writeStartArray()
        v.foreach(writeJson(gen, _, fmt))
        gen.writeEndArray()
      case null => gen.writeNull()
      case v =>
        throw new java.lang.RuntimeException(
          s"unable to convert '$v' [${v.getClass}] to json"
        )
    }
  }

  /** convert a JsonNode to a scala value */
  def fromJson(node: JsonNode): Any = {
    import scala.collection.JavaConverters._
    node match {
      case _ if node.isBigDecimal => node.getDecimalValue
      case _ if node.isBigInteger => node.getBigIntegerValue
      case _ if node.isBoolean    => node.getBooleanValue
      case _ if node.isLong       => node.getLongValue
      case _ if node.isInt        => node.getIntValue
      case _ if node.isDouble     => node.getDoubleValue
      case _ if node.isTextual    => node.getTextValue
      case _ if node.isNull       => null
      case _ if node.isObject =>
        node.getFieldNames.asScala
          .map(
            key => key -> fromJson(node.get(key))
          )
          .toMap
      case _ if node.isArray => node.getElements.asScala.map(fromJson).toList
      case _ =>
        throw new java.lang.RuntimeException("unable to convert from Json to Scala: " + node)
    }
  }

  /** default date formatter used for json pretty-printing objects with dates in them */
  def dateFormatter(arg: Any): Any = {
    arg match {
      case v: Date => dateFormat.format(v)
      case v: DateTime =>
        v.toDateTime(org.joda.time.DateTimeZone.UTC).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      case v => v
    }
  }

  /** diff multiple records, return unifed diff output
    *
    * @param recs records to diff, pre-sorted, oldest first
    * @param context how much diff context to return, default is entire document
    * @param prefix uri prefix using when writing new/old document names for diff output
    * @return string of unified diff output
    */
  def diffRecords(recs: Seq[Record], context: Option[Int] = None, prefix: String = ""): String = {
    import difflib.DiffUtils
    import difflib.Patch

    import scala.collection.JavaConverters._

    if (recs.size < 2) {
      throw new java.lang.RuntimeException("diff requires at least 2 records")
    }

    // map each record to a tuple of it's id uri and pretty-printed string output
    // then use 2-wide sliding window and create unified diffs for each pair
    val result = new collection.mutable.StringBuilder

    recs
      .map(rec => {
        if (rec == null) {
          ("/dev/null", "")
        } else {
          val baos = new ByteArrayOutputStream()
          val gen = factory.createJsonGenerator(baos, UTF8)
          val dpp = new DefaultPrettyPrinter
          dpp.indentArraysWith(new DefaultPrettyPrinter.Lf2SpacesIndenter)
          gen.setPrettyPrinter(dpp)
          writeJson(gen, rec.data, dateFormatter)
          gen.close()
          (prefix + "/" + rec.id + ";_pp;_at=" + rec.stime.getMillis, baos.toString)
        }
      })
      .sliding(2)
      .foreach(v => {
        val (a, b) = (v.head, v.tail.head)
        val aLines = a._2.split("\n").toList
        val bLines = b._2.split("\n").toList

        val size = {
          if (context.isDefined) context.get
          else if (aLines.length > bLines.length) aLines.length
          else bLines.length
        }

        val patch: Patch = DiffUtils.diff(bLines.asJava, aLines.asJava)

        DiffUtils
          .generateUnifiedDiff(b._1, a._1, bLines.asJava, patch, size)
          .asScala
          .foreach(l => {
            result.append(l)
            result.append('\n')
          })
      })

    result.toString()
  }

  /** utility to turn a matrix argument string into a map
    *  note: the string must start with ';'
    */
  def parseMatrixArguments(arguments: String): Map[String, String] = arguments match {
    case m if m == null || m == "" =>
      Map()
    // skip null/or empty matrix (ie ";;a=b"), also map value null to matrix args missing value
    case _ =>
      arguments.tail
        .split(";")
        .map(_ split "=")
        .collect({
          case Array(k, v)                    => (k, v)
          case Array(m) if m != ""            => (m, null)
          case v: Array[String] if v.size > 2 => (v.head, v.tail.fold("")(_ + "=" + _))
        })
        .toMap
  }

  def uuid: String = java.util.UUID.randomUUID.toString

  def compress(in: String): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val gzip = new GZIPOutputStream(out)
    gzip.write(in.getBytes("UTF-8"))
    gzip.close()
    out.toByteArray
  }

  def decompress(in: Array[Byte]): String = {
    val gis = new GZIPInputStream(new ByteArrayInputStream(in))
    IOUtils.toString(gis, "UTF-8")
  }

  def makeHistoryDatastore(name: String): Option[Datastore] = {
    Common
      .getProperty("edda", "datastore.class", name, "com.netflix.edda.mongo.MongoDatastore")
      .get match {
      case datastoreClassName: String if datastoreClassName != "" =>
        val datastoreClass = this.getClass.getClassLoader.loadClass(datastoreClassName)
        val datastoreCtor = datastoreClass.getConstructor(classOf[String])
        Some(datastoreCtor.newInstance(name).asInstanceOf[Datastore])
      case _ =>
        None
    }
  }

  def makeCurrentDatastore(name: String): Option[Datastore] = {
    Common.getProperty("edda", "datastore.current.class", name, "").get match {
      case datastoreClassName: String if datastoreClassName != "" =>
        val datastoreClass = this.getClass.getClassLoader.loadClass(datastoreClassName)
        val datastoreCtor = datastoreClass.getConstructor(classOf[String])
        Some(datastoreCtor.newInstance(name).asInstanceOf[Datastore])
      case _ =>
        None
    }
  }
}
