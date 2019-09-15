package com.netflix.edda.crawlers.aws

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest
import com.netflix.edda.actors.Observable
import com.netflix.edda.actors.ObserverExecutionContext
import com.netflix.edda.actors.RequestId
import com.netflix.edda.actors.StateMachine
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.crawlers.CrawlerState
import com.netflix.edda.records.Record
import com.netflix.edda.records.RecordSet
import com.netflix.edda.util.Common
import org.slf4j.LoggerFactory

import scala.actors.Actor

case class AwsHostedRecordCrawlerState(hostedZones: Seq[Record] = Seq[Record]())

object AwsHostedRecordCrawler extends StateMachine.LocalState[AwsHostedRecordCrawlerState]

/** crawler for Route53 Resource Record Sets (DNS records)
  *  this is a secondary crawler that crawls the resource recordsets for each hosted zone
  * and then pulls out each recordset in the zones to track them seperately
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  * @param crawler the awsHostedZone crawler
  */
class AwsHostedRecordCrawler(val name: String, val ctx: AwsCrawler.Context, val crawler: Crawler)
  extends Crawler {

  import AwsHostedRecordCrawler._

  override def crawl()(implicit req: RequestId) {}

  // we dont crawl, just get updates from crawler when it crawls
  override def doCrawl()(implicit req: RequestId) =
    throw new java.lang.UnsupportedOperationException(
      "doCrawl() should not be called on HostedRecordCrawler"
    )

  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val threadPool = Executors.newFixedThreadPool(10)

  /** for each zone call listResourceRecordSets and map that to a new document
    *
    * @param zones the records to crawl
    * @return the record set for the resourceRecordSet
    */
  def doCrawl(zones: Seq[Record])(implicit req: RequestId): Seq[Record] = {

    val futures: Seq[java.util.concurrent.Future[Seq[Record]]] = zones.map(
      zone => {
        val zoneId = zone.data.asInstanceOf[Map[String, Any]]("id").asInstanceOf[String]
        val zoneName = zone.id
        val request = new ListResourceRecordSetsRequest(zoneId)
        threadPool.submit(
          new Callable[Seq[Record]] {
            def call() = {
              val it = new AwsIterator() {
                def next() = {
                  val response = backoffRequest {
                    ctx.awsClient.r53
                      .listResourceRecordSets(request.withStartRecordName(this.nextToken.get))
                  }
                  this.nextToken = Option(response.getNextRecordName)
                  response.getResourceRecordSets.asScala
                    .map(
                      item => {
                        Record(
                          item.getName,
                          ctx.beanMapper(item).asInstanceOf[Map[String, Any]] ++ Map(
                            "zone" -> Map("id" -> zoneId, "name" -> zoneName)
                          )
                        )
                      }
                    )
                    .toList
                }
              }
              it.toList.flatten
            }
          }
        )
      }
    )
    var failed: Boolean = false
    val records = futures
      .map(
        f => {
          try Some(f.get)
          catch {
            case e: Exception => {
              failed = true
              if (logger.isErrorEnabled)
                logger.error(s"$req$this exception from listResourceRecordSets", e)
              None
            }
          }
        }
      )
      .collect({
        case Some(rec) => rec
      })
      .flatten

    if (failed) {
      throw new java.lang.RuntimeException(s"$this failed to crawl resource record sets")
    }
    records
  }

  protected override def initState =
    addInitialState(super.initState, newLocalState(AwsHostedRecordCrawlerState()))

  protected override def init() {
    implicit val req = RequestId("init")
    Common.namedActor(this + " init") {
      import ObserverExecutionContext._
      crawler.addObserver(this) onComplete {
        case scala.util.Failure(msg) => {
          if (logger.isErrorEnabled)
            logger.error(
              s"$req${Actor.self} failed to add observer $this to $crawler: $msg, retrying"
            )
          this.init
        }
        case scala.util.Success(msg) => super.init
      }
    }
  }

  protected def localTransitions: PartialFunction[(Any, StateMachine.State), StateMachine.State] = {
    case (gotMsg @ Crawler.CrawlResult(from, hostedZoneSet), state) => {
      implicit val req = gotMsg.req
      // this is blocking so we dont crawl in parallel
      if (hostedZoneSet.records ne localState(state).hostedZones) {
        val newRecords = doCrawl(hostedZoneSet.records)
        Observable
          .localState(state)
          .observers
          .foreach(
            _ ! Crawler
              .CrawlResult(this, RecordSet(newRecords, Map("source" -> "crawl", "req" -> req.id)))
          )
        setLocalState(
          Crawler.setLocalState(state, CrawlerState(newRecords)),
          AwsHostedRecordCrawlerState(hostedZoneSet.records)
        )
      } else state
    }
  }

  override protected def transitions = localTransitions orElse super.transitions
}
