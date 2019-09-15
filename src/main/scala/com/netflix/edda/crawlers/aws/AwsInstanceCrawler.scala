package com.netflix.edda.crawlers.aws

import com.netflix.edda.actors.Observable
import com.netflix.edda.actors.ObserverExecutionContext
import com.netflix.edda.actors.RequestId
import com.netflix.edda.actors.StateMachine
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.crawlers.CrawlerState
import com.netflix.edda.records.Record
import com.netflix.edda.records.RecordSet
import com.netflix.edda.util.Common
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class AwsInstanceCrawlerState(reservationRecords: Seq[Record] = Seq[Record]())

object AwsInstanceCrawler extends StateMachine.LocalState[AwsInstanceCrawlerState]

/** crawler for Instances
  *
  * this is a secondary crawler that takes the results from the AwsReservationCrawler
  * and then pulls out each instance in the reservations to track them seperately
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  * @param crawler the AwsReservation crawler
  */
class AwsInstanceCrawler(val name: String, val ctx: AwsCrawler.Context, val crawler: Crawler)
  extends Crawler {

  import AwsInstanceCrawler._

  private[this] val logger = LoggerFactory.getLogger(getClass)

  override def crawl()(implicit req: RequestId) {}

  // we dont crawl, just get updates from crawler when it crawls
  override def doCrawl()(implicit req: RequestId) =
    throw new java.lang.UnsupportedOperationException(
      "doCrawl() should not be called on InstanceCrawler"
    )

  def doCrawl(resRecords: Seq[Record]): Seq[Record] = {
    resRecords.flatMap(rec => {
      rec.data.asInstanceOf[Map[String, Any]].get("instances") match {
        case Some(instances: Seq[_]) =>
          instances
            .asInstanceOf[Seq[Map[String, Any]]]
            .map(
              (inst: Map[String, Any]) =>
                rec.copy(
                  id = inst("instanceId").asInstanceOf[String],
                  data = inst,
                  ctime = inst("launchTime").asInstanceOf[DateTime]
                )
            )
        case other =>
          throw new java.lang.RuntimeException(
            "failed to crawl instances from reservation, got: " + other
          )
      }
    })
  }

  protected override def initState =
    addInitialState(super.initState, newLocalState(AwsInstanceCrawlerState()))

  protected override def init() {
    implicit val req = RequestId("init")
    Common.namedActor(this + " init") {
      import ObserverExecutionContext._
      crawler.addObserver(this) onComplete {
        case scala.util.Failure(msg) => {
          if (logger.isErrorEnabled)
            logger.error(s"$req{Actor.self} failed to add observer $this $crawler: $msg, retrying")
          this.init
        }
        case scala.util.Success(msg) => super.init
      }
    }
  }

  protected def localTransitions: PartialFunction[(Any, StateMachine.State), StateMachine.State] = {
    case (gotMsg @ Crawler.CrawlResult(from, reservationSet), state) => {
      implicit val req = gotMsg.req
      // this is blocking so we dont crawl in parallel
      if (reservationSet.records ne localState(state).reservationRecords) {
        val newRecords = doCrawl(reservationSet.records)
        Observable
          .localState(state)
          .observers
          .foreach(
            _ ! Crawler
              .CrawlResult(this, RecordSet(newRecords, Map("source" -> "crawl", "req" -> req.id)))
          )
        setLocalState(
          Crawler.setLocalState(state, CrawlerState(newRecords)),
          AwsInstanceCrawlerState(reservationSet.records)
        )
      } else state
    }
  }

  override protected def transitions = localTransitions orElse super.transitions
}
