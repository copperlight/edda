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

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import com.netflix.edda.util.Common
import com.typesafe.scalalogging.StrictLogging

import scala.actors.Actor
import scala.actors.TIMEOUT
import scala.actors.scheduler.ExecutorScheduler

/** holds base message type for all state machine transition messages. */
object StateMachine {

  /** basic state for each StateMachine.  The String will be the name of the class
    * in the inheritance chain and the value will be whatever class that object
    * wants to store for state (usually a case class) */
  type State = Map[String, Any]

  /** Helper types to keep type annotations short throughout the code. */
  type Transition = PartialFunction[(Message, State), State]
  type LocalTransition = PartialFunction[(Any, State), State]
  type Handler = PartialFunction[Exception, Unit]

  /** all messages to a StateMachine need to extend Message */
  abstract class Message()(implicit requestId: RequestId) {
    def req: RequestId = requestId
    def from: Actor
  }

  /** message to Stop the StateMachine */
  case class Stop(from: Actor)(implicit req: RequestId) extends Message {}

  /** trait to determine if message is an error type */
  trait ErrorMessage extends Message {}

  /** sent in case the message does not extend Message trait */
  case class InvalidMessageError(
    from: Actor,
    reason: String, message: Any
  )(implicit req: RequestId) extends ErrorMessage

  /** sent in the case there are no matching case clauses for the the incoming message */
  case class UnknownMessageError(
    from: Actor,
    reason: String, message: Any
  )(implicit req: RequestId) extends ErrorMessage

  /** keep track of a local state for each subclass of the StateMachine. For the inheritance of
    * Collection->Queryable->Observable->StateMachine we could have separate states
    * for Collection, Queryable, and Observable. (in this case Queryable has no internal state)
    * The LocalState routines are imported typically via a companion object like:
    *
    * {{{
    * object Collection extends StateMachine.LocalState[CollectionState] {...}
    *
    * class Collection {
    *   import Collection._
    *
    *   protected override def initState = {
    *     addInitialState(super.initState, newLocalState(CollectionState(...)))
    *   }
    * }}}
    */
  class LocalState[T] {
    def localStateKey: String = this.getClass.getName

    /** should be called from from StateMachine.initState to initialize a new local state */
    def newLocalState(init: T): (String, T) = {
      localStateKey -> init
    }

    /** update local state for your StateMachine
      * {{{
      *   setLocalState(state, localState(state).copy(crawled = newRecords))
      * }}}
      */
    def setLocalState(state: StateMachine.State, localState: T): State = {
      state + (localStateKey -> localState)
    }

    /** get the state for your local class */
    def localState(state: StateMachine.State): T = {
      state.get(localStateKey) match {
        case Some(localState) =>
          localState.asInstanceOf[T]
        case _ =>
          throw new java.lang.RuntimeException(s"$localStateKey state missing from current state")
      }
    }
  }

}

/** Base class for our state machine.
  *
  * The state is stored in immutable data, so we will always have a consistent state.
  */
class StateMachine extends Actor with StrictLogging {

  import StateMachine._

  protected def init(): Unit = {
    val msg = 'INIT
    logger.debug(s"${Actor.self} sending: $msg -> $this")
    this ! msg
  }

  def threadPoolSize = 4
  val pool: ExecutorService = Executors.newFixedThreadPool(threadPoolSize)
  override val scheduler = ExecutorScheduler(pool, term = false)

  /** subclasses need to overload this routine when local state is required:
    *
    * {{{
    *   protected override def initState = {
    *     addInitialState(
    *       super.initState,
    *       newLocalState(CollectionState(records = load(replicaOk = false)))
    *     )
  *     }
    * }}}
    *
    * @return
    */
  protected def initState: State = {
    Map()
  }

  /** stop the state machine */
  def stop()(implicit req: RequestId): Unit = {
    val msg = Stop(Actor.self)
    logger.debug(s"$req${Actor.self} sending: $msg -> $this")
    this ! msg
  }

  /** used from subclasses initState routine to add their localState object to the overall StateMachine state */
  protected def addInitialState(state: State, stateTup: (String, Any)): State = {
    val (localStateKey, initValue) = stateTup

    if (state.isDefinedAt(localStateKey)) {
      throw new java.lang.RuntimeException(s"State for $localStateKey already initialized")
    } else {
      state + (localStateKey -> initValue)
    }
  }

  /** used to drain the actor mailbox of messages when desired.
    *
    * {{{
    * flushMessages {
    *   case Crawl(from) => true
    * }
    * }}}
    *
    */
  protected def flushMessages(pf: PartialFunction[Any, Boolean]): Unit = {
    var keepLooping = true

    while (keepLooping) {
      keepLooping = Actor.self.receiveWithin(0)(pf orElse {
        case TIMEOUT => false
      })
    }
  }

  /** PartialFunction to allow Messages to transition the state machine from one state to another.  Subclasses
    * must override this routine to handle new messages types.
    *
    * {{{
    *   private def localTransitions: PartialFunction[(Any, StateMachine.State), StateMachine.State] = {
    *     case (NewMessage,state) => setLocalState(state, localState(state).copy(value=newValue))
    *   }
    *   override protected def transitions = localTransitions orElse super.transitions
    * }}}
    *
    * @return
    */
  protected def transitions: Transition = {
    case (UnknownMessageError(_, reason, _), _) =>
      throw new java.lang.RuntimeException(reason)
    case (InvalidMessageError(_, reason, _), _) =>
      throw new java.lang.RuntimeException(reason)
  }

  /** the main loop for the StateMachine actor.  It will call initState then start looping
    * and react'ing to messages until it gets a Stop message. */
  final def act(): Unit = {
    init()

    Actor.self.react {
      case msg @ 'INIT =>
        var state = Common.RETRY {
          initState
        }

        logger.debug(s"$this received: $msg from $sender")
        var keepLooping = true

        Actor.self.loopWhile(keepLooping) {
          Actor.self.react {
            case gotMsg @ Stop(_) =>
              implicit val req: RequestId = gotMsg.req

              logger.debug(s"$req$this received: $gotMsg from $sender")
              keepLooping = false

            case gotMsg: Message =>
              implicit val req: RequestId = gotMsg.req

              if (!transitions.isDefinedAt(gotMsg, state)) {
                logger.error(s"$req Unknown Message $gotMsg sent from $sender")

                val msg = UnknownMessageError(this, s"Unknown Message $gotMsg", gotMsg)
                logger.debug(s"$req$this sending: $msg -> $sender")
                sender ! msg
              }

              logger.debug(s"$req$this received: $gotMsg from $sender")

              try {
                state = transitions(gotMsg, state)
              } catch {
                case e: Exception =>
                  logger.error(s"$req failed to handle event $gotMsg", e)
              }

            case message =>
              logger.error(s"Invalid Message $message sent from $sender")

              val msg = InvalidMessageError(this, s"Invalid Message $message", message)(RequestId())
              logger.debug(s"$this sending: $msg -> $sender")
              sender ! msg
          }
        }
    }
  }

  var handlers: Handler = {
    case e: Exception => logger.error(s"$this caught exception", e)
  }

  /** add a partial function to allow for specific exception handling when needed
    *
    * @param pf PartialFunction to handle exception types
    */
  def addExceptionHandler(pf: Handler): Unit = {
    handlers = pf orElse handlers
  }

  /** setup exceptionHandler to use the custom handlers modified with addExceptionHandler
    */
  override def exceptionHandler: Handler = {
    handlers
  }
}
