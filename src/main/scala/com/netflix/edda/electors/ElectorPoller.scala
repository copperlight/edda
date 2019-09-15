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
package com.netflix.edda.electors

import com.netflix.edda.actors.RequestId
import com.netflix.edda.actors.StateMachine
import com.netflix.edda.util.Common
import com.typesafe.scalalogging.StrictLogging

import scala.actors.Actor
import scala.actors.TIMEOUT

class ElectorPoller(elector: Elector) extends Actor with StrictLogging {

  override def toString: String = s"$elector poller"

  override def act(): Unit = {
    var keepLooping = true

    Actor.self.loopWhile(keepLooping) {
      implicit val req: RequestId = RequestId(s"${Common.uuid} poller")

      Actor.self.reactWithin(elector.pollCycle.get.toInt) {
        case _ @StateMachine.Stop(_) =>
          keepLooping = false
        case got @ TIMEOUT =>
          logger.debug(s"$req$this received: $got")
          val msg = Elector.RunElection(Actor.self)
          logger.debug(s"$req$this sending: $msg -> $elector")
          elector ! msg
      }
    }
  }

  override def exceptionHandler: StateMachine.Handler = {
    case e: Exception =>
      logger.error(s"$this failed to setup election poller", e)
  }

  def stop()(implicit req: RequestId): Unit = {
    val msg = StateMachine.Stop(this)
    logger.debug(s"$req$this sending: $msg -> $this")
    this ! msg
  }
}
