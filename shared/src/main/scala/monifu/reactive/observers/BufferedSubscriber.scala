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
 
package monifu.reactive.observers

import monifu.concurrent.Scheduler
import monifu.reactive.OverflowStrategy._
import monifu.reactive._

/**
 * Interface describing [[monifu.reactive.Observer Observer]] wrappers
 * that are thread-safe (can receive concurrent events) and that
 * return an immediate `Continue` when receiving `onNext`
 * events. Meant to be used by data sources that cannot uphold the
 * no-concurrent events and the back-pressure related requirements
 * (i.e. data-sources that cannot wait on `Future[Continue]` for
 * sending the next event).
 *
 * Implementations of this interface have the following contract:
 *
 *  - `onNext` / `onError` / `onComplete` of this interface MAY be
 *    called concurrently
 *  - `onNext` SHOULD return an immediate `Continue`, as long as the
 *    buffer is not full and the underlying observer hasn't signaled
 *    `Cancel` (N.B. due to the asynchronous nature, `Cancel` signaled
 *    by the underlying observer may be noticed later, so
 *    implementations of this interface make no guarantee about queued
 *    events - which could be generated, queued and dropped on the
 *    floor later)
 *  - `onNext` MUST return an immediate `Cancel` result, after it
 *    notices that the underlying observer signaled `Cancel` (due to
 *    the asynchronous nature of observers, this may happen later and
 *    queued events might get dropped on the floor)
 *  - in general the contract for the underlying Observer is fully
 *    respected (grammar, non-concurrent notifications, etc...)
 *  - when the underlying observer canceled (by returning `Cancel`),
 *    or when a concurrent upstream data source triggered an error,
 *    this SHOULD eventually be noticed and acted upon
 *  - as long as the buffer isn't full and the underlying observer
 *    isn't `Cancel`, then implementations of this interface SHOULD
 *    not lose events in the process
 *  - the buffer MAY BE either unbounded or bounded, in case of
 *    bounded buffers, then an appropriate overflowStrategy needs to be set for
 *    when the buffer overflows - either an `onError` triggered in the
 *    underlying observer coupled with a `Cancel` signaled to the
 *    upstream data sources, or dropping events from the head or the
 *    tail of the queue, or attempting to apply back-pressure, etc...
 *
 * See [[monifu.reactive.OverflowStrategy OverflowStrategy]] for the buffer
 * policies available.
 */
trait BufferedSubscriber[-T] extends Subscriber[T]

object BufferedSubscriber {
  def apply[T](observer: Observer[T], bufferPolicy: OverflowStrategy)
      (implicit s: Scheduler): BufferedSubscriber[T] = {

    bufferPolicy match {
      case Unbounded =>
        SynchronousBufferedSubscriber.unbounded(observer)
      case Fail(bufferSize) =>
        SynchronousBufferedSubscriber.overflowTriggering(observer, bufferSize)
      case BackPressure(bufferSize) =>
        BackPressuredBufferedSubscriber(observer, bufferSize)
      case DropNew(bufferSize) =>
        DropIncomingBufferedSubscriber.simple(observer, bufferSize)
      case DropOld(bufferSize) =>
        EvictingBufferedSubscriber.dropOld(observer, bufferSize)
      case ClearBuffer(bufferSize) =>
        EvictingBufferedSubscriber.clearBuffer(observer, bufferSize)
    }
  }

  private[reactive] def apply[T]
    (observer: Observer[T], strategy: OverflowStrategy, onOverflow: Long => T)
    (implicit s: Scheduler): BufferedSubscriber[T] = {

    strategy match {
      case withSignal: WithSignal if onOverflow != null =>
        withOverflowSignal(observer, withSignal)(onOverflow)
      case _ =>
        apply(observer, strategy)
    }
  }

  def withOverflowSignal[T](observer: Observer[T], overflowStrategy: OverflowStrategy.WithSignal)
    (onOverflow: Long => T)
    (implicit s: Scheduler): BufferedSubscriber[T] = {

    overflowStrategy match {
      case DropNew(bufferSize) =>
        DropIncomingBufferedSubscriber.withSignal(observer, bufferSize, onOverflow)

      case DropOld(bufferSize) =>
        EvictingBufferedSubscriber.dropOld(observer, bufferSize, onOverflow)

      case ClearBuffer(bufferSize) =>
        EvictingBufferedSubscriber.clearBuffer(observer, bufferSize, onOverflow)
    }
  }
}
