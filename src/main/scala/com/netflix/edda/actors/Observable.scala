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
package com.netflix.edda.actors

import com.netflix.edda.actors.StateMachine.Transition
import com.netflix.edda.util.Common
import com.typesafe.scalalogging.StrictLogging

import scala.actors.Actor
import scala.actors.TIMEOUT

/** local state for StateMachine */
case class ObservableState(observers: Set[Actor] = Set[Actor]())

/** contains messages for StateMachine */
object Observable extends StateMachine.LocalState[ObservableState] {

  /** Message to add observer to local state */
  case class Observe(
    from: Actor,
    actor: Actor
  )(implicit req: RequestId) extends StateMachine.Message

  /** Message to remove observer from local state */
  case class Ignore(from: Actor, actor: Actor)(implicit req: RequestId) extends StateMachine.Message

  /** Response to use for sync calls to add/remove observers */
  case class OK(from: Actor)(implicit req: RequestId) extends StateMachine.Message
}

/** register or un-register observers with various StateMachine objects.  This is needed if
  * one StateMachine needs to receive events (state changes) from another StateMachine.  For
  * example a Collection is registered as an observer of a Crawler so
  * that the Collection gets updates when the Crawler state changes (so that the Collection
  * can persist the changes to the Datastore).
  */
abstract class Observable extends StateMachine {

  import Observable._

  /** notify the given actor when the state changes */
  def addObserver(
    actor: Actor
  )(implicit req: RequestId): scala.concurrent.Future[StateMachine.Message] = {

    val p = scala.concurrent.promise[StateMachine.Message]

    Common.namedActor(s"$this observer client") {
      val msg = Observe(Actor.self, actor)

      logger.debug(s"$req${Actor.self} sending: $msg -> $this with 60s timeout")
      this ! msg

      Actor.self.reactWithin(60000) {
        case msg: OK =>
          logger.debug(s"$req${Actor.self} received: $msg from $sender")
          p success msg
        case msg @ TIMEOUT =>
          logger.debug(s"$req${Actor.self} received: $msg")
          p failure new java.util.concurrent.TimeoutException("Failed to addObserver after 60s")
      }
    }

    p.future
  }

  /** stop notifying the give actor when the state changes */
  def delObserver(
    actor: Actor
  )(implicit req: RequestId): scala.concurrent.Future[StateMachine.Message] = {

    val p = scala.concurrent.promise[StateMachine.Message]

    Common.namedActor(this + " observer client") {
      val msg = Ignore(Actor.self, actor)

      logger.debug(s"$req${Actor.self} sending: $msg -> $this with 60s timeout")
      this ! msg

      Actor.self.reactWithin(60000) {
        case msg: OK =>
          logger.debug(s"$req${Actor.self} received: $msg from $sender")
          p success msg
        case msg @ TIMEOUT =>
          logger.debug(s"$req${Actor.self} received: $msg")
          p failure new java.util.concurrent.TimeoutException("Failed to delObserver after 60s")
      }
    }

    p.future
  }

  protected override def initState: StateMachine.State = {
    addInitialState(super.initState, newLocalState(ObservableState()))
  }

  /** setup transitions to handle Observe and Ignore messages */
  private def localTransitions: PartialFunction[(Any, StateMachine.State), StateMachine.State] = {
    case (gotMsg @ Observe(_, caller), state) =>
      implicit val req: RequestId = gotMsg.req
      val msg = OK(this)
      logger.debug(s"$req$this sending: $msg -> $sender")
      sender ! msg
      setLocalState(state, ObservableState(localState(state).observers + caller))

    case (gotMsg @ Ignore(_, caller), state) =>
      implicit val req: RequestId = gotMsg.req
      val msg = OK(this)
      logger.debug(s"$req$this sending: $msg -> $sender")
      sender ! msg
      setLocalState(state, ObservableState(localState(state).observers diff Set(caller)))
  }

  override protected def transitions: Transition = {
    localTransitions orElse super.transitions
  }
}
