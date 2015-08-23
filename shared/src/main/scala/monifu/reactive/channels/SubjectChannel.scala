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
 
package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.OverflowStrategy.WithSignal
import monifu.reactive._
import monifu.reactive.observers.BufferedSubscriber

/**
 * Wraps any [[Subject]] into a [[Channel]].
 */
class SubjectChannel[-I,+O] private[reactive]
    (subject: Subject[I, O], overflowStrategy: OverflowStrategy.Synchronous, onOverflow: Long => I)
    (implicit scheduler: Scheduler)
  extends Channel[I] with Observable[O] {

  assert(onOverflow == null || overflowStrategy.isInstanceOf[WithSignal],
    "onOverflow is only supported for `OverflowStrategy.WithSignal`")

  private[this] val channel =
    BufferedSubscriber(subject, overflowStrategy, onOverflow)

  final def subscribeFn(subscriber: Subscriber[O]): Unit = {
    subject.unsafeSubscribe(subscriber)
  }

  final def pushNext(elems: I*): Unit = {
    for (elem <- elems) channel.observer.onNext(elem)
  }

  final def pushComplete(): Unit = {
    channel.observer.onComplete()
  }

  final def pushError(ex: Throwable): Unit = {
    channel.observer.onError(ex)
  }
}

object SubjectChannel {
  /**
   * Wraps any [[Subject]] into a [[Channel]].
   *
   * @param strategy - the [[OverflowStrategy overflow strategy]]
   *        used for buffering, which specifies what to do in case
   *        we're dealing with slow consumers: should an unbounded
   *        buffer be used, should back-pressure be applied, should
   *        the pipeline drop newer or older events, should it drop
   *        the whole buffer?  See [[OverflowStrategy]] for more
   *        details.
   */
  def apply[I,O](subject: Subject[I, O], strategy: OverflowStrategy.Synchronous)
    (implicit s: Scheduler): SubjectChannel[I, O] = {

    new SubjectChannel[I,O](subject, strategy, null)
  }

  /**
   * Wraps any [[Subject]] into a [[Channel]].
   *
   * @param strategy - the [[OverflowStrategy overflow strategy]]
   *        used for buffering, which specifies what to do in case
   *        we're dealing with slow consumers: should an unbounded
   *        buffer be used, should back-pressure be applied, should
   *        the pipeline drop newer or older events, should it drop
   *        the whole buffer?  See [[OverflowStrategy]] for more
   *        details.
   *
   * @param onOverflow - a function that is used for signaling a special
   *        event used to inform the consumers that an overflow event
   *        happened, function that receives the number of dropped
   *        events as a parameter (see [[OverflowStrategy.WithSignal]])
   */
  def apply[I,O](subject: Subject[I, O], strategy: OverflowStrategy.WithSignal, onOverflow: Long => I)
    (implicit s: Scheduler): SubjectChannel[I, O] = {

    new SubjectChannel[I,O](subject, strategy, onOverflow)
  }
}
