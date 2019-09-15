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
package com.netflix.edda.crawlers

import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import com.amazonaws.AmazonClientException
import com.netflix.config.DynamicStringProperty
import com.netflix.edda.actors
import com.netflix.edda.actors.Observable
import com.netflix.edda.actors.RequestId
import com.netflix.edda.actors.StateMachine
import com.netflix.edda.actors.StateMachine.Transition
import com.netflix.edda.records.Record
import com.netflix.edda.records.RecordSet
import com.netflix.edda.util.Common
import com.netflix.servo.DefaultMonitorRegistry
import com.netflix.servo.monitor.Monitors

import scala.actors.Actor
import scala.concurrent.Future
import scala.util.Random

/** local state for Crawlers
  *
  * @param records the records that were crawled
  */
case class CrawlerState(records: Seq[Record] = Seq[Record]())

/** companion object for [[Crawler]].  */
object Crawler extends StateMachine.LocalState[CrawlerState] {

  /** Message sent to Observers */
  case class CrawlResult(
    from: Actor,
    newRecordSet: RecordSet
  )(implicit req: RequestId)
      extends StateMachine.Message {

    override def toString: String = {
      s"CrawlResult(req=$req, newRecords=${newRecordSet.records.size} meta=${newRecordSet.meta})"
    }
  }

  /** Message to start a crawl action */
  case class Crawl(from: Actor)(implicit req: RequestId) extends StateMachine.Message
}

/** Crawler to crawl something and generate Records based on what was crawled.
  * Those records are then passed to a Collection (typically) by sending the
  * crawl results to all observers.
  */
abstract class Crawler extends Observable {

  import Crawler._
  import com.netflix.edda.util.Common._

  lazy val enabled: DynamicStringProperty =
    Common.getProperty("edda.crawler", "enabled", name, "true")
  lazy val jitter: DynamicStringProperty =
    Common.getProperty("edda.crawler", "jitter.enabled", name, "false")
  lazy val throttle: DynamicStringProperty =
    Common.getProperty("edda.crawler", "throttle.enabled", name, "false")
  lazy val throttle_delay: DynamicStringProperty =
    Common.getProperty("edda.crawler", "throttle.delay", name, "200")
  lazy val retry_max: DynamicStringProperty =
    Common.getProperty("edda.crawler", "throttle.maxDelayMultiplier", name, "225")
  lazy val request_delay: DynamicStringProperty =
    Common.getProperty("edda.crawler", "requestDelay", name, "0")

  /* number of retries attempted */
  var retry_count = 0

  /** start a crawl if the crawler is enabled */
  def crawl()(implicit req: RequestId) {
    if (enabled.get.toBoolean) {
      val msg = Crawl(Actor.self)
      logger.debug(s"$req${Actor.self} sending: $msg -> $this")
      this ! msg
    }
  }

  /** see [[Observable.addObserver]].  Overridden to be a NoOp when Crawler is not enabled */
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

  /** see [[actors.Observable.delObserver]].  Overridden to be a NoOp when Crawler is not enabled */
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

  /** name of the Crawler, typically matches the name of the Collection that the Crawler works with */
  def name: String

  // basic servo metrics
  private[this] val crawlTimer = Monitors.newTimer("crawl")
  private[this] val crawlCounter = Monitors.newCounter("crawl.count")
  private[this] val errorCounter = Monitors.newCounter("crawl.errors")

  /** abstract routine for subclasses to implement the actual crawl logic */
  protected def doCrawl()(implicit req: RequestId): Seq[Record]

  /** initialize Crawler state */
  protected override def initState: StateMachine.State = {
    addInitialState(super.initState, newLocalState(CrawlerState()))
  }

  /** init just registers metrics for Servo */
  protected override def init() {
    Monitors.registerObject("edda.crawler." + name, this)

    DefaultMonitorRegistry
      .getInstance()
      .register(
        Monitors.newThreadPoolMonitor(
          s"edda.crawler.$name.threadpool",
          this.pool.asInstanceOf[ThreadPoolExecutor]
        )
      )

    super.init()
  }

  /** handle Crawl Messages to the StateMachine */
  private def localTransitions: Transition = {
    case (gotMsg @ Crawl(_), state) =>
      implicit val req: RequestId = gotMsg.req

      // this is blocking so we don't crawl in parallel

      // in case we are crawling slower than expected
      // we might have a bunch of Crawl messages in the
      // mailbox, so just burn through them now
      flushMessages {
        case Crawl(_) => true
      }

      val stopwatch = crawlTimer.start()

      val newRecords = try {
        doCrawl()
      } catch {
        case e: Exception =>
          errorCounter.increment()
          throw e
      } finally {
        stopwatch.stop()
      }

      logger.info(
        "{} {} Crawled {} records in {} sec",
        toObjects(
          req,
          this,
          newRecords.size,
          stopwatch.getDuration(TimeUnit.MILLISECONDS) / 1000d -> "%.2f"
        )
      )

      crawlCounter.increment(newRecords.size)

      Observable
        .localState(state)
        .observers
        .foreach(o => {
          val msg = Crawler
            .CrawlResult(this, RecordSet(newRecords, Map("source" -> "crawl", "req" -> req.id)))
          logger.debug(s"$req$this sending: $msg -> $o")
          o ! msg
        })

      /* reset the error count at the end of each run */
      retry_count = 0
      setLocalState(state, CrawlerState(records = newRecords))
  }

  /** wrapper for all requests to throttle or delay access to the AWS API */
  def backoffRequest[T](code: => T): T = {
    // using a ratio of 2:1 for errors to retries due to requests being sent concurrently
    val errorReducer = 2

    // number of "free" errors to ignore before full throttling is enabled
    val margin = 10
    val marginalDelay = throttle_delay.get.toInt * (((retry_count - margin) / errorReducer) + 1)
    val throttleDelay = throttle_delay.get.toInt * ((retry_count / errorReducer) + 1)

    /* configurable delay for all requests to slow down access */
    if (request_delay.get.toInt > 0) Thread sleep request_delay.get.toInt

    /* configurable random delay to further throttle access */
    if (jitter.get.toBoolean) {
      val maxJitter = Common.getProperty("edda.crawler", "jitter.max", name, "2000").get.toInt
      val rand = new Random
      val jitter = (maxJitter * rand.nextDouble).toLong
      logger.debug("{} api request delayed by {}ms", this, jitter)
      Thread sleep jitter
    }

    /* handler for AWS imposed throttling */
    if (throttle.get.toBoolean) {
      try {
        if (retry_count > margin) {
          logger.debug("{} SLEEPING [{}]: {}", Array(this, retry_count, marginalDelay))
          Thread sleep marginalDelay
        }

        code
      } catch {
        case e: AmazonClientException =>
          val pattern = ".*Error Code: ([A-Za-z]+);.*".r
          val pattern(err_code) = e.getMessage

          if ((err_code == "RequestLimitExceeded") || (err_code == "Throttling")) {
            Thread sleep throttleDelay
            retry_count = retry_count + 1
            if ((retry_count / errorReducer) >= retry_max.get.toInt) {
              logger.error("Hit configured maximum number of API backoff requests, aborting")
              throw e
            }
            backoffRequest { code }
          } else {
            logger.error("Unexpected AmazonClientException, aborting")
            throw e
          }
        case e: Exception =>
          logger.error("Unexpected Exception, aborting")
          throw e
      }
    } else {
      code
    }
  }

  protected override def transitions: Transition = {
    localTransitions orElse super.transitions
  }

  override def toString: String = s"[Crawler $name]"
}
