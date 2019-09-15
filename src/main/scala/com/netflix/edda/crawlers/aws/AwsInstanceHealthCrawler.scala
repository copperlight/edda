package com.netflix.edda.crawlers.aws

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest
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
import org.slf4j.LoggerFactory

import scala.actors.Actor
import scala.collection.JavaConverters._

case class AwsInstanceHealthCrawlerState(elbRecords: Seq[Record] = Seq[Record]())

object AwsInstanceHealthCrawler extends StateMachine.LocalState[AwsInstanceHealthCrawlerState]

/** crawler for LoadBalancer Instances
  *
  * this is a secondary crawler that takes the results from the AwsLoadBalancerCrawler
  * and then crawls the instance states for each ELB.
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  * @param crawler the LoadBalancer crawler
  */
class AwsInstanceHealthCrawler(val name: String, val ctx: AwsCrawler.Context, val crawler: Crawler)
  extends Crawler {

  import java.util.concurrent.TimeUnit

  import com.netflix.servo.monitor.Monitors

  val crawlTimer = Monitors.newTimer("crawl")

  import AwsInstanceHealthCrawler._

  override def crawl()(implicit req: RequestId) {}

  // we don't crawl, just get updates from crawler when it crawls
  override def doCrawl()(implicit req: RequestId) =
    throw new java.lang.UnsupportedOperationException(
      "doCrawl() should not be called on InstanceHealthCrawler"
    )

  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val threadPool = Executors.newFixedThreadPool(10)

  /** for each elb call describeInstanceHealth and map that to a new document
    *
    * @param elbRecords the records to crawl
    * @return the record set for the instanceHealth
    */
  def doCrawl(elbRecords: Seq[Record])(implicit req: RequestId): Seq[Record] = {
    val futures: Seq[java.util.concurrent.Future[Record]] = elbRecords.map(
      elb => {
        threadPool.submit(
          new Callable[Record] {
            def call() = {
              try {
                val instances = backoffRequest {
                  ctx.awsClient.elb
                    .describeInstanceHealth(new DescribeInstanceHealthRequest(elb.id))
                    .getInstanceStates
                }
                elb.copy(
                  data =
                    Map("name" -> elb.id, "instances" -> instances.asScala.map(ctx.beanMapper(_)))
                )
              } catch {
                case e: Exception => {
                  throw new java.lang.RuntimeException(
                    this + " describeInstanceHealth failed for ELB " + elb.id,
                    e
                  )
                }
              }
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
                logger.error(s"$req$this exception from describeInstanceHealth", e)
              None
            }
          }
        }
      )
      .collect {
        case Some(rec) => rec
      }

    if (failed) {
      throw new java.lang.RuntimeException(s"$this failed to crawl instance health")
    }
    records
  }

  protected override def initState =
    addInitialState(super.initState, newLocalState(AwsInstanceHealthCrawlerState()))

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
    case (gotMsg @ Crawler.CrawlResult(from, elbRecordSet), state) => {
      implicit val req = gotMsg.req
      // this is blocking so we dont crawl in parallel
      if (elbRecordSet.records ne localState(state).elbRecords) {
        val stopwatch = crawlTimer.start()
        val newRecords = doCrawl(elbRecordSet.records)
        stopwatch.stop()
        if (logger.isInfoEnabled)
          logger.info(
            "{} {} Crawled {} records in {} sec",
            Common.toObjects(
              req,
              this,
              newRecords.size,
              stopwatch.getDuration(TimeUnit.MILLISECONDS) / 1000d -> "%.2f"
            )
          )
        Observable
          .localState(state)
          .observers
          .foreach(
            _ ! Crawler
              .CrawlResult(this, RecordSet(newRecords, Map("source" -> "crawl", "req" -> req.id)))
          )
        /* reset retry count at end of run for backoff */
        retry_count = 0
        setLocalState(
          Crawler.setLocalState(state, CrawlerState(newRecords)),
          AwsInstanceHealthCrawlerState(elbRecordSet.records)
        )
      } else state
    }
  }

  override protected def transitions = localTransitions orElse super.transitions
}
