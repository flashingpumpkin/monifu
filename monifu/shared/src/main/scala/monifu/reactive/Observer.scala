/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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
 
package monifu.reactive

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.observers.{SynchronousObserver, SynchronousSubscriber}
import monifu.reactive.streams.{SubscriberAsObserver, SubscriberAsReactiveSubscriber, SynchronousSubscriberAsReactiveSubscriber}
import org.reactivestreams.{Subscriber => RSubscriber}
import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal


/**
 * The Observer from the Rx pattern is the trio of callbacks that
 * get subscribed to an Observable for receiving events.
 *
 * The events received must follow the Rx grammar, which is:
 *      onNext *   (onComplete | onError)?
 *
 * That means an Observer can receive zero or multiple events, the stream
 * ending either in one or zero `onComplete` or `onError` (just one, not both),
 * and after onComplete or onError, a well behaved Observable implementation
 * shouldn't send any more onNext events.
 */
trait Observer[-T] {
  def onNext(elem: T): Future[Ack]

  def onError(ex: Throwable): Unit

  def onComplete(): Unit
}

object Observer {
  /**
   * Given an `org.reactivestreams.Subscriber` as defined by the 
   * [[http://www.reactive-streams.org/ Reactive Streams]] specification, 
   * it builds an [[Observer]] instance compliant with the 
   * Monifu Rx implementation.
   */
  def fromReactiveSubscriber[T](subscriber: RSubscriber[T])(implicit s: Scheduler): Observer[T] = {
    SubscriberAsObserver(subscriber)
  }

  /**
   * Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
   * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification.
   */
  def toReactiveSubscriber[T](observer: Observer[T])(implicit s: Scheduler): RSubscriber[T] = {
    toReactiveSubscriber(observer, s.env.batchSize)(s)
  }

  /**
   * Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
   * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification.
   *
   * @param bufferSize a strictly positive number, representing the size
   *                   of the buffer used and the number of elements requested
   *                   on each cycle when communicating demand, compliant with
   *                   the reactive streams specification
   */
  def toReactiveSubscriber[T](observer: Observer[T], bufferSize: Int)(implicit s: Scheduler): RSubscriber[T] = {
    require(bufferSize > 0, "requestCount > 0")
    observer match {
      case sync: SynchronousObserver[_] =>
        val inst = sync.asInstanceOf[SynchronousObserver[T]]
        SynchronousSubscriberAsReactiveSubscriber(SynchronousSubscriber(inst, s), bufferSize)
      case async =>
        SubscriberAsReactiveSubscriber(Subscriber(async, s), bufferSize)
    }
  }

  /**
   * Extension methods for [[Observer]].
   */
  implicit class Extensions[T](val source: Observer[T]) extends AnyVal {
    /**
     * Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
     * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
     * specification.
     */
    def toReactiveSubscriber(implicit s: Scheduler): RSubscriber[T] =
      Observer.toReactiveSubscriber(source)

    /**
     * Transforms the source [[Observer]] into a `org.reactivestreams.Subscriber`
     * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
     * specification.
     *
     * @param bufferSize a strictly positive number, representing the size
     *                   of the buffer used and the number of elements requested
     *                   on each cycle when communicating demand, compliant with
     *                   the reactive streams specification
     */
    def toReactiveSubscriber(bufferSize: Int)(implicit s: Scheduler): RSubscriber[T] =
      Observer.toReactiveSubscriber(source, bufferSize)


    /**
     * Feeds the given [[Observer]] instance with elements from the given iterable,
     * respecting the contract and returning a `Future[Ack]` with the last
     * acknowledgement given after the last emitted element.
     */
    def feed(iterable: Iterable[T])(implicit s: Scheduler): Future[Ack] = {
      def scheduleFeedLoop(promise: Promise[Ack], iterator: Iterator[T]): Future[Ack] = {
        s.execute(new Runnable {
          private[this] val modulus = s.env.batchSize - 1

          @tailrec
          def fastLoop(syncIndex: Int): Unit = {
            val ack = source.onNext(iterator.next())

            if (iterator.hasNext) {
              val nextIndex = if (!ack.isCompleted) 0 else
                (syncIndex + 1) & modulus

              if (nextIndex != 0) {
                if (ack == Continue || ack.value.get == Continue.IsSuccess)
                  fastLoop(nextIndex)
                else
                  promise.complete(ack.value.get)
              }
              else ack.onComplete {
                case Continue.IsSuccess =>
                  run()
                case other =>
                  promise.complete(other)
              }
            }
            else {
              promise.completeWith(ack)
            }
          }

          def run(): Unit = {
            try fastLoop(0) catch {
              case NonFatal(ex) =>
                try source.onError(ex) finally {
                  promise.failure(ex)
                }
            }
          }
        })

        promise.future
      }

      val iterator = iterable.iterator
      if (iterator.hasNext)
        scheduleFeedLoop(Promise[Ack](), iterator)
      else
        Continue
    }
  }
}
