/*
 * Copyright 2012-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.edda.collections

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.Callable
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import com.netflix.config.DynamicStringProperty
import com.netflix.edda.actors
import com.netflix.edda.actors.Observable
import com.netflix.edda.actors.Queryable
import com.netflix.edda.actors.RequestId
import com.netflix.edda.actors.StateMachine
import com.netflix.edda.actors.StateMachine.Transition
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.datastores.Datastore
import com.netflix.edda.electors.Elector
import com.netflix.edda.records.Record
import com.netflix.edda.records.RecordMatcher
import com.netflix.edda.records.RecordSet
import com.netflix.edda.util.Common
import com.netflix.servo.DefaultMonitorRegistry
import com.netflix.servo.monitor.BasicGauge
import com.netflix.servo.monitor.MonitorConfig
import com.netflix.servo.monitor.Monitors
import org.codehaus.jackson.JsonEncoding.UTF8
import org.codehaus.jackson.map.MappingJsonFactory
import org.joda.time.DateTime

import scala.actors.Actor
import scala.actors.TIMEOUT
import scala.concurrent.Future
import scala.util.Random

/** local state class for Collection
  *
  * @param recordSet the current active records for the collection
  */
case class CollectionState(recordSet: RecordSet = RecordSet())

/** companion object for Collection*/
object Collection extends StateMachine.LocalState[CollectionState] {

  /** Collections need a recordMatcher as well as the ConfigContext to handle querying the inMemory record set. */
  trait Context {
    def recordMatcher: RecordMatcher
  }

  /** class to represent a record that has changed, used for the Datastore to update records */
  case class RecordUpdate(oldRecord: Record, newRecord: Record)

  /** class to represent a complete delta between old record set and new record set (new from Crawler)
    *
    * @param recordSet the current set of active records
    * @param changed the list of RecordUpdate for records that have changed
    * @param added   the list of new records (new records that Crawler found)
    * @param removed the list of records that are not longer active (were not returned from Crawler)
    */
  case class Delta(
    recordSet: RecordSet,
    changed: Seq[RecordUpdate],
    added: Seq[Record],
    removed: Seq[Record]
  ) {
    override def toString: String = {
      "Delta(records=" + recordSet.records.size + ", changed=" + changed.size + ", added=" + added.size + ", removed=" + removed.size + ")"
    }
  }

  /** Message to Purge the record set from the Datastore */
  case class Purge(from: Actor)(implicit req: RequestId) extends StateMachine.Message

  case class UpdateOK(from: Actor, delta: Delta, origMeta: Map[String, Any] = Map())(
    implicit req: RequestId
  ) extends StateMachine.Message

  object RetentionPolicy extends Enumeration {
    type RetentionPolicy = Value
    val ALL, LIVE, LAST = Value
  }

  object PurgePolicy extends Enumeration {
    type PurgePolicy = Value
    val NONE, LIVE, LAST, AGE = Value
  }

  private lazy val jsonFactory = new MappingJsonFactory
}

/** general Collection logic.  It is abstract to specify the collection name,
  * responsible Crawler, and optional Datastore and the Elector to determine leadership.
  *
  * @param ctx context to get recordMatcher
  */
abstract class Collection(val ctx: Collection.Context) extends Queryable {

  import Collection._
  import Common._

  lazy val enabled: DynamicStringProperty =
    Common.getProperty("edda.collection", "enabled", name, "true")

  // pull out the purgePolicy and any options from strings like:
  // edda.collection.purgePolicy=AGE;expiry=2678400000
  lazy val purgeProperty: DynamicStringProperty =
    Common.getProperty("edda.collection", "purgePolicy", name, "NONE")

  /** name of the collection, typically the name of the corresponding crawler also.  Something like
    * test.us-east-1.aws.autoScalingGroups
    */
  def name: String

  /** the Crawler that will we will observe for Crawled records.  The Crawler will send us
    * records and we will compare them with our in-memory records to determine changes.
    */
  def crawler: Crawler

  val processor = new CollectionProcessor(this)
  val refresher = new CollectionRefresher(this)

  /** the optional abstracted Datastore.  MongoDB is currently the only available Datastore
    * but more could be added.  It is optional so you can run without a datastore, although many
    * features will be limited (only current state is available, so no history queries possible)
    */
  lazy val dataStore: Option[Datastore] = Common.makeHistoryDatastore(name)

  lazy val currentDataStore: Option[Datastore] = {
    val ds = Common.makeCurrentDatastore(name)
    if (ds.isDefined) ds else dataStore
  }

  /** The elector to determine leadership. This is typically a singleton so all Collections share
    * the same Election results, but it could be customized if we need to have multiple leaders handling
    * different Collections.
    */
  def elector: Elector

  /** allow option to skip cache usage and go straight to datastore
    */
  lazy val liveOverride: DynamicStringProperty =
    Common.getProperty("edda.collection", "noCache", name, "false")

  lazy val diskCache: DynamicStringProperty =
    Common.getProperty("edda.collection", "diskCache", name, "")

  lazy val ignoreHistoryUpdateFailures: DynamicStringProperty =
    Common.getProperty("edda.collection", "ignoreHistoryUpdateFailures", name, "false")

  /** see [[Queryable.query]].  Overridden to return Nil when Collection is not enabled */
  override def query(
    queryMap: Map[String, Any] = Map(),
    limit: Int = 0,
    live: Boolean = false,
    keys: Set[String] = Set(),
    replicaOk: Boolean = false
  )(implicit req: RequestId): scala.concurrent.Future[Seq[Record]] = {

    import com.netflix.edda.actors.QueryExecutionContext._

    if (enabled.get.toBoolean)
      super.query(queryMap, limit, live || liveOverride.get.toBoolean, keys, replicaOk)
    else
      Future {
        Seq.empty
      }
  }

  /** see [[Observable.addObserver]].  Overridden to be a NoOp when Collection is not enabled */
  override def addObserver(
    actor: Actor
  )(implicit req: RequestId): scala.concurrent.Future[StateMachine.Message] = {

    import com.netflix.edda.actors.ObserverExecutionContext._

    if (enabled.get.toBoolean)
      super.addObserver(actor)
    else
      Future {
        Observable.OK(Actor.self)
      }
  }

  /** see [[actors.Observable.delObserver]].  Overridden to be a NoOp when Collection is not enabled */
  override def delObserver(
    actor: Actor
  )(implicit req: RequestId): scala.concurrent.Future[StateMachine.Message] = {

    import com.netflix.edda.actors.ObserverExecutionContext._

    if (enabled.get.toBoolean)
      super.delObserver(actor)
    else
      Future {
        Observable.OK(Actor.self)
      }
  }

  // basic servo metrics
  private[this] val loadTimer = Monitors.newTimer("load")
  private[this] val loadCounter = Monitors.newCounter("load.count")
  private[this] val loadErrorCounter = Monitors.newCounter("load.errors")

  private[this] val loadRecordCount = new AtomicLong(0)
  private[this] val loadRecordGauge = new BasicGauge[java.lang.Long](
    MonitorConfig.builder("load.recordCount").build(),
    new Callable[java.lang.Long] {
      def call: Long = loadRecordCount.get
    }
  )

  private[this] val updatedRecordCount = new AtomicLong(0)
  private[this] val updatedRecordGauge = new BasicGauge[java.lang.Long](
    MonitorConfig.builder("crawl.updatedRecordCount").build(),
    new Callable[java.lang.Long] {

      def call: Any = {
        if (elector.isLeader()(RequestId("crawl.updatedRecordCount.gauge")))
          updatedRecordCount.get
        else
          0
      }
    }
  )

  private[this] val addedRecordCount = new AtomicLong(0)
  private[this] val addedRecordGauge = new BasicGauge[java.lang.Long](
    MonitorConfig.builder("crawl.addedRecordCount").build(),
    new Callable[java.lang.Long] {

      def call(): Any = {
        if (elector.isLeader()(RequestId("crawl.addedRecordCount.gauge")))
          addedRecordCount.get
        else
          0
      }
    }
  )

  private[this] val deletedRecordCount = new AtomicLong(0)
  private[this] val deletedRecordGauge = new BasicGauge[java.lang.Long](
    MonitorConfig.builder("crawl.deletedRecordCount").build(),
    new Callable[java.lang.Long] {

      def call: Any = {
        if (elector.isLeader()(RequestId("crawl.deletedRecordCount.gauge")))
          deletedRecordCount.get
        else
          0
      }
    }
  )

  private[this] var lastCurrentUpdate = DateTime.now
  private[this] val currentUpdateGauge = new BasicGauge[java.lang.Long](
    MonitorConfig.builder("lastCurrentUpdate").build(),
    new Callable[java.lang.Long] {

      def call: Any = {
        if (currentDataStore.isDefined && elector.isLeader()(RequestId("lastCurrentUpdateGauge")))
          DateTime.now.getMillis - lastCurrentUpdate.getMillis
        else
          0
      }
    }
  )

  private[this] var lastHistoryUpdate = DateTime.now
  private[this] val historyUpdateGauge = new BasicGauge[java.lang.Long](
    MonitorConfig.builder("lastHistoryUpdate").build(),
    new Callable[java.lang.Long] {

      def call: Any = {
        if (dataStore.isDefined && elector.isLeader()(RequestId("lastHistoryUpdateGauge")))
          DateTime.now.getMillis - lastHistoryUpdate.getMillis
        else
          0
      }
    }
  )

  var lastMtimeUpdated: DateTime = new DateTime(0)
  var lastMtime: DateTime = new DateTime(0)

  /** query datastore or in memory collection. */
  protected def doQuery(
    queryMap: Map[String, Any],
    limit: Int,
    live: Boolean,
    keys: Set[String],
    replicaOk: Boolean,
    state: StateMachine.State
  )(implicit req: RequestId): Seq[Record] = {
    // generate function
    if (live || liveOverride.get.toBoolean) {
      if (dataStore.isDefined) {
        return dataStore.get.query(queryMap, limit, keys, replicaOk)
      } else {
        logger.warn(s"$req Datastore is not available, applying query to cached records")
      }
    }

    val t0 = System.nanoTime()

    try {
      val recs = if (queryMap.isEmpty) {
        firstOf(limit, localState(state).recordSet.records)
      } else {
        firstOf(
          limit,
          localState(state).recordSet.records
            .filter(record => ctx.recordMatcher.doesMatch(queryMap, record.toMap))
        )
      }

      localState(state).recordSet.meta.get("mtime") match {
        case Some(date: DateTime) =>
          recs.map(_.copy(mtime = date))
        case _ =>
          recs
      }
    } finally {
      val t1 = System.nanoTime()
      val lapse = (t1 - t0) / 1000000
      logger.info(s"$req$this memory scan lapse: ${lapse}ms")
    }
  }

  /** load collection from Datastore (if available) */
  protected def load(replicaOk: Boolean)(implicit req: RequestId): RecordSet = {
    if (currentDataStore.isDefined) {
      val now = DateTime.now

      val recordSet = try {
        currentDataStore.get.load(replicaOk)
      } catch {
        case _: java.lang.UnsupportedOperationException =>
          // this can happen when currentDataStore has not been initalized yet, so pull from
          // the history datastore instead
          if (dataStore.isDefined) dataStore.get.load(replicaOk) else RecordSet()
      }

      lastLoad = recordSet.records match {
        case Nil       => now
        case _: Seq[_] => recordSet.records.maxBy(_.mtime.getMillis).mtime
      }

      loadRecordCount.set(recordSet.records.size)
      recordSet
    } else {
      logger.warn(s"$req Datastore is not available for load()")
      RecordSet()
    }
  }

  protected[edda] def update(d: Delta)(implicit req: RequestId): Delta = {
    loadRecordCount.set(d.recordSet.records.size)
    updatedRecordCount.set(d.changed.size)
    addedRecordCount.set(d.added.size)
    deletedRecordCount.set(d.removed.size)

    val d1 = {
      if (currentDataStore.isDefined) {
        val delta = currentDataStore.get.update(d)
        lastCurrentUpdate = DateTime.now
        delta
      } else {
        d
      }
    }

    val d2 = {
      if (dataStore.isDefined && ((currentDataStore.isDefined && currentDataStore.get != dataStore.get) || currentDataStore.isEmpty)) {
        // merge the meta data for the datastore updates
        try {
          val newDelta = dataStore.get.update(d)
          lastHistoryUpdate = DateTime.now
          d1.copy(
            recordSet = d1.recordSet.copy(meta = newDelta.recordSet.meta ++ d1.recordSet.meta)
          )
        } catch {
          case e: Exception =>
            logger.error(s"$req$this Failed update datastore: $e", e)
            if (!ignoreHistoryUpdateFailures.get.toBoolean) throw e else d1
        }
      } else {
        d1
      }
    }

    d2
  }

  /** customize how a record change is handled.  If it returns true
    * a new document revision is created in the dataStore, if it is false
    * the original document is updated (new document revision not created)
    */
  protected def newStateTimeForChange(newRec: Record, oldRec: Record) = true

  /** calculate the difference between the records from a Crawl result and the records
    * currently in memory.
    *
    * @param newRecordSet records from the Crawler
    * @param oldRecordSet records from previous Delta result
    */
  protected[edda] def delta(newRecordSet: RecordSet, oldRecordSet: RecordSet)(
    implicit req: RequestId
  ): Delta = {
    val now = DateTime.now
    val newRecords = newRecordSet.records.map(rec => rec.copy(mtime = now))

    // remove needs to be a list to allow for duplicate records (multiple record revisions
    // on the same id)
    var remove = Seq[Record]()

    // sometimes there are duplicates in oldRecords (upon first-load when we load all records
    // with null ltime) when we have a rogue writer (sometimes there are gaps between leadership
    // changes).
    val oldSeen = scala.collection.mutable.Map[String, Record]()

    val oldMap = oldRecordSet.records
      .filter(r => {
        val in = oldSeen.contains(r.id)
        if (in) {
          val lastSeen = oldSeen(r.id).mtime
          remove +:= r.copy(mtime = lastSeen, ltime = lastSeen)
        } else {
          oldSeen += (r.id -> r)
        }
        !in
      })
      .map(rec => rec.id -> rec)
      .toMap

    val newMap = newRecords.map(rec => rec.id -> rec).toMap

    remove ++= oldMap
      .filterNot(pair => newMap.contains(pair._1))
      .map(pair => pair._2.copy(mtime = now, ltime = now))

    val addedMap = newMap.filterNot(pair => oldMap.contains(pair._1))

    val changes = newMap
      .filter(pair => {
        oldMap.contains(pair._1) && !pair._2.sameData(oldMap(pair._1))
      })
      .map(
        pair => {
          val oldRec = oldMap(pair._1)
          val newRec = pair._2

          if (newStateTimeForChange(newRec, oldRec)) {
            pair._1 -> Collection.RecordUpdate(
              oldRec.copy(mtime = now, ltime = now),
              newRec.copy(ctime = oldRec.ctime, ftime = oldRec.ftime)
            )
          } else {
            pair._1 -> Collection.RecordUpdate(
              oldRec.copy(mtime = now, ltime = now),
              newRec.copy(ctime = oldRec.ctime, ftime = oldRec.ftime, stime = oldRec.stime)
            )
          }
        }
      )

    // need to reset stime, ctime, tags for crawled records to match what we have in memory
    val fixedRecords = newRecords.collect {
      case rec: Record if changes.contains(rec.id) =>
        val newRec = changes(rec.id).newRecord
        oldMap(rec.id).copy(data = rec.data, mtime = newRec.mtime, stime = newRec.stime)
      case rec: Record if oldMap.contains(rec.id) =>
        oldMap(rec.id).copy(data = rec.data, mtime = rec.mtime)
      case rec: Record =>
        rec
    }

    logger.info(
      s"$req$this total: ${fixedRecords.size} changed: ${changes.size} added: ${addedMap.size} removed: ${remove.size} meta: ${oldRecordSet.meta ++ newRecordSet.meta}"
    )

    Delta(
      RecordSet(fixedRecords, oldRecordSet.meta ++ newRecordSet.meta),
      changed = changes.values.toSeq,
      added = addedMap.values.toSeq,
      removed = remove
    )
  }

  /** setup CollectionState, initialize the records to be loaded from the Datastore before the Actor starts accepting message */
  protected[edda] override def initState: StateMachine.State = {
    addInitialState(
      super.initState,
      newLocalState(CollectionState(recordSet = doLoad(replicaOk = true)(RequestId("initState"))))
    )
  }

  /** initialize servo metrics for Collection.  Delay start based on random jitter to prevent Datastore from being
    * overloaded by all Collection loading all at once.
    */
  protected override def init() {
    implicit val req: RequestId = RequestId("init")
    Monitors.registerObject("edda.collection." + name, this)

    DefaultMonitorRegistry
      .getInstance()
      .register(
        Monitors.newThreadPoolMonitor(
          s"edda.collection.$name.threadpool",
          this.pool.asInstanceOf[ThreadPoolExecutor]
        )
      )

    Common.namedActor(s"$this init") {
      // create routine to run after the jitter timeout
      // or to run immediately if jitter is disabled
      def postJitter(): Unit = {
        if (currentDataStore.isDefined) currentDataStore.get.init()

        if (dataStore.isDefined && ((currentDataStore.isDefined && currentDataStore.get != dataStore.get) || currentDataStore.isEmpty))
          dataStore.get.init()

        // routine to run on success of crawler addObserver call
        // or to run immediately if crawler is disabled
        def postObserver(): Unit = {
          refresher.start()

          // super.init will cause normal event processing to start on this
          // collection actor, so the next addObserver should proceed
          super.init()

          // listen to our own DeltaResult events
          def retry(): Unit = {
            import com.netflix.edda.actors.ObserverExecutionContext._

            processor.addObserver(processor) onFailure {
              case msg =>
                logger.error(
                  s"$req${Actor.self} failed to add observer $processor to $processor with error: $msg, retrying"
                )
                retry()
            }
          }

          retry()
        }

        if (Option(crawler).isDefined) {
          import com.netflix.edda.actors.ObserverExecutionContext._

          crawler.addObserver(processor) onComplete {
            case scala.util.Success(_) =>
              postObserver()
            case scala.util.Failure(msg) =>
              logger.error(
                s"$req${Actor.self} failed to add observer $processor to $crawler with error: $msg, retrying"
              )
              postJitter()
          }
        } else {
          postObserver()
        }
      }

      if (Common.getProperty("edda.collection", "jitter.enabled", name, "true").get.toBoolean) {
        val cacheRefresh =
          Common.getProperty("edda.collection", "cache.refresh", name, "10000").get.toLong

        val maxJitter = if (cacheRefresh > 30000) 30000 else cacheRefresh

        // adding in random jitter on start so we dont crush the datastore immediately if multiple
        // systems are coming up at the same time
        val rand = new Random
        val jitter = (maxJitter * rand.nextDouble).toLong

        logger.info(s"$req$this start delayed by ${jitter}ms")

        Actor.self.reactWithin(jitter) {
          case msg @ TIMEOUT =>
            logger.debug(s"$req${Actor.self} received: $msg for jitter timeout")
            postJitter()
        }
      } else {
        postJitter()
      }
    }
  }

  /** some collections do not need to trigger crawl requests directly
    * in the case where the are downstream of another collection/crawler that
    * just post-processes crawl results from another collection.
    */
  protected[edda] def allowCrawl = true

  private[this] var lastLoad: DateTime = new DateTime(0)
  private[edda] var lastPurge: DateTime = DateTime.now()

  /** load records from Datastore and update monitoring metrics */
  private[edda] def doLoad(replicaOk: Boolean)(implicit req: RequestId): RecordSet = {
    val stopwatch = loadTimer.start()
    val recordSet = try {
      load(replicaOk)
    } catch {
      case e: Exception =>
        loadErrorCounter.increment()
        logger.error(s"$req$this failed to load", e)
        throw e
    } finally {
      stopwatch.stop()
    }
    loadCounter.increment()

    logger.info(
      "{}{} Loaded {} records in {} sec",
      toObjects(
        req,
        this,
        recordSet.records.size,
        stopwatch.getDuration(TimeUnit.MILLISECONDS) / 1000.0 -> "%.2f"
      )
    )

    recordSet.copy(meta = recordSet.meta + ("source" -> "load") + ("req" -> req.id))
  }

  /** handle Collection Messages */
  private def localTransitions: PartialFunction[(Any, StateMachine.State), StateMachine.State] = {
    case (gotMsg @ Purge(_), state) =>
      implicit val req: RequestId = gotMsg.req

      flushMessages {
        case Purge(_) => true
      }

      lastPurge = DateTime.now

      import com.netflix.edda.actors.PurgeExecutionContext._

      Future {
        if (dataStore.isDefined) {
          val purgeArgs: Map[String, String] = Common.parseMatrixArguments(s";${purgeProperty.get}")
          val policyName = (PurgePolicy.values.map(_.toString) & purgeArgs.keySet).head
          val purgePolicy = PurgePolicy.withName(policyName)
          val purgePolicyOptions = purgeArgs - policyName

          purgePolicy match {
            case PurgePolicy.NONE =>
            case PurgePolicy.LIVE =>
              dataStore.get.remove(Map("ltime" -> Map("$ne" -> null)))
            case PurgePolicy.LAST =>
              logger.warn(s"$req$this LAST PurgePolicy is not yet implemented")
            case PurgePolicy.AGE =>
              val options = purgePolicyOptions.asInstanceOf[Map[String, String]]
              if (options.contains("expiry")) {
                val expiry = options("expiry").toLong
                dataStore.get.remove(
                  Map("ltime" -> Map("$lt" -> new DateTime(DateTime.now.getMillis - expiry)))
                )
              } else {
                logger.error(
                  s"$req$this AGE PurgePolicy requires expiry option to be specified, such as AGE;expiry=2678400000"
                )
              }
          }
        }
      } onFailure {
        case err: Throwable => logger.error(s"$req$this purge processor failed", err)
        case err            => logger.error(s"$req$this purge processor failed: $err")
      }

      state
    case (gotMsg @ UpdateOK(_, d, _), state) =>
      implicit val req: RequestId = gotMsg.req

      val cacheDir = diskCache.get

      if (!cacheDir.isEmpty) {
        val t0 = System.nanoTime()

        try {
          val uuid = Common.uuid
          val dir = Paths.get(cacheDir)

          if (Files.notExists(dir)) {
            Files.createDirectories(dir)
          }

          val realPath = dir.resolve(name + "." + uuid)
          val fos = new java.io.FileOutputStream(realPath.toString)

          try {
            val gen = jsonFactory.createJsonGenerator(fos, UTF8)
            try {
              Common.writeJson(gen, d.recordSet.records.map(_.data))
            } finally {
              gen.close()
            }
          } finally {
            fos.close()
          }

          try {
            val symPath = dir.resolve(name)

            if (Files.isSymbolicLink(symPath)) {
              val oldFile = Files.readSymbolicLink(symPath)
              // can't replace symlink in one operation, so make tmp symlink
              // and move it over the old one
              val tmpSymPath = dir.resolve(name + "." + Common.uuid)
              Files.createSymbolicLink(tmpSymPath, realPath)
              Files.move(tmpSymPath, symPath, REPLACE_EXISTING)
              Files.delete(oldFile)
            } else {
              Files.createSymbolicLink(symPath, realPath)
            }
          } catch {
            case e: Exception =>
              logger.error(s"$req$this failed to create disk cache: ${e.getMessage}", e)
          }
        } finally {
          val t1 = System.nanoTime()
          val lapse = (t1 - t0) / 1000000
          logger.info(s"$req$this writing disk cache: ${lapse}ms")
        }
      }

      Observable
        .localState(state)
        .observers
        .foreach(o => {
          val msg = gotMsg.copy(from = this)
          logger.debug(s"$req$this sending: $msg -> $o")
          o ! msg
        })

      setLocalState(state, localState(state).copy(recordSet = d.recordSet))
  }

  override protected def transitions: Transition = {
    localTransitions orElse super.transitions
  }

  override def toString: String = s"[Collection $name]"

  /** if collection is enabled start elector, start crawler first */
  override def start(): Actor = {
    implicit val req = RequestId("start")
    if (enabled.get.toBoolean) {
      logger.info(s"$req Starting $this")
      Option(elector).foreach(_.start())
      Option(crawler).foreach(_.start())
      processor.start()
      super.start()
    } else {
      logger.info(s"$req Collection $name is disabled, not starting")
      this
    }
  }

  /** stop elector, crawler and shutdown ForkJoin special scheduler */
  override def stop()(implicit req: RequestId) {
    logger.info(s"$req Stopping $this")
    Option(elector).foreach(_.stop())
    Option(crawler).foreach(_.stop())
    processor.stop()
    refresher.stop()
    super.stop()
  }
}
