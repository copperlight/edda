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

import java.util.concurrent.ThreadPoolExecutor

import com.netflix.edda.actors.Queryable
import com.netflix.edda.actors.RequestId
import com.netflix.edda.actors.StateMachine
import com.netflix.edda.actors.StateMachine.Transition
import com.netflix.edda.records.Record
import com.netflix.servo.DefaultMonitorRegistry
import com.netflix.servo.monitor.Monitors

import scala.actors.Actor

/** a pseudo collection made up of other related collections.  This allows multiple collections
  * of the same type (but crawled for different accounts) to appear as one unified
  * collection
  *
  * @param name the root name of the collection (usually name from RootCollection base class)
  * @param collections list of common collections that should appear unified
  */
class MergedCollection(val name: String, val collections: Seq[Collection]) extends Queryable {
  override def toString: String = s"[MergedCollection $name]"

  import Queryable._

  /** handle Query Message for MergedCollection */
  private def localTransitions: PartialFunction[(Any, StateMachine.State), StateMachine.State] = {
    case (gotMsg @ Query(_, _, limit, _, _, _), state) =>
      implicit val req: RequestId = gotMsg.req

      val replyTo = sender

      import com.netflix.edda.actors.QueryExecutionContext._

      scala.concurrent.future {
        if (collections.size == 1) {
          collections.head.query(
            gotMsg.query,
            gotMsg.limit,
            gotMsg.live,
            gotMsg.keys,
            gotMsg.replicaOk
          ) onComplete {
            case scala.util.Success(recs: Seq[Record]) =>
              val msg = QueryResult(this, recs)
              logger.debug(s"$req${Actor.self} sending: $msg -> $replyTo")
              replyTo ! msg
            case scala.util.Failure(error) =>
              logger.error(
                s"$req query on ${collections.head} failed: $gotMsg with error: $error"
              )
              val msg = QueryError(this, error)
              logger.debug(s"$req${Actor.self} sending: $msg -> $replyTo")
              replyTo ! msg
          }
        } else {
          val futures = collections.map(
            _.query(gotMsg.query, gotMsg.limit, gotMsg.live, gotMsg.keys, gotMsg.replicaOk)
          )

          try {
            val recs = futures.flatMap { f =>
              scala.concurrent.Await.result(
                f,
                scala.concurrent.duration.Duration(60000, scala.concurrent.duration.MILLISECONDS)
              )
            }

            val msg =
              QueryResult(this, firstOf(limit, recs.sortWith((a, b) => a.stime.isAfter(b.stime))))

            logger.debug(s"$req${Actor.self} sending: $msg -> $replyTo")
            replyTo ! msg
          } catch {
            case e: Exception =>
              val msg = QueryError(this, e)
              logger.debug(s"$req${Actor.self} sending: $msg -> $replyTo")
              replyTo ! msg
          }
        }
      }

      state
  }

  override protected def transitions: Transition = {
    localTransitions orElse super.transitions
  }

  protected def doQuery(
    queryMap: Map[String, Any],
    limit: Int,
    live: Boolean,
    keys: Set[String],
    replicaOk: Boolean,
    state: StateMachine.State
  )(implicit req: RequestId): Seq[Record] = {
    throw new java.lang.RuntimeException("doQuery on MergedCollection should not be called")
  }

  /** start the actors for all the merged collections then start this actor */
  override def start(): Actor = {
    Monitors.registerObject("edda.collection.merged." + name, this)

    DefaultMonitorRegistry
      .getInstance()
      .register(
        Monitors.newThreadPoolMonitor(
          s"edda.collection.merged.$name.threadpool",
          this.pool.asInstanceOf[ThreadPoolExecutor]
        )
      )

    logger.info("Starting " + this)
    collections.foreach(_.start())
    super.start()
  }

  /** stop the actors for all the merged collections then stop this actor */
  override def stop()(implicit req: RequestId) {
    logger.info("Stopping " + this)
    collections.foreach(_.stop())
    super.stop()
  }
}
