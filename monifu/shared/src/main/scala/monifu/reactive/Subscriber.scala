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
import monifu.reactive.observers.{SynchronousObserver, SynchronousSubscriber}
import monifu.reactive.streams.{ReactiveSubscriberAsMonifuSubscriber, SubscriberAsReactiveSubscriber, SynchronousSubscriberAsReactiveSubscriber}
import org.reactivestreams.{Subscriber => RSubscriber}
import scala.concurrent.Future

/**
 * A `Subscriber` value is a named tuple of an observer and a scheduler,
 * whose usage is in [[Observable.create]].
 *
 * An `Observable.subscribe` takes as parameters both an [[Observer]]
 * and a [[monifu.concurrent.Scheduler Scheduler]] and the purpose of a
 * `Subscriber` is convenient grouping in `Observable.create`.
 *
 * A `Subscriber` value is basically an address that the data source needs
 * in order to send events.
 */
trait Subscriber[-T] extends Observer[T] {
  implicit def scheduler: Scheduler
}

object Subscriber {
  /** Subscriber builder */
  def apply[T](observer: Observer[T], scheduler: Scheduler): Subscriber[T] =
    observer match {
      case ref: Subscriber[_] if ref.scheduler == scheduler =>
        ref.asInstanceOf[Subscriber[T]]
      case ref: SynchronousObserver[_] =>
        SynchronousSubscriber(ref.asInstanceOf[SynchronousObserver[T]], scheduler)
      case _ =>
        new Implementation[T](observer, scheduler)
    }

  /**
   * Given an `org.reactivestreams.Subscriber` as defined by the 
   * [[http://www.reactive-streams.org/ Reactive Streams]] specification, 
   * it builds an [[Subscriber]] instance compliant with the
   * Monifu Rx implementation.
   */
  def fromReactiveSubscriber[T](subscriber: RSubscriber[T])(implicit s: Scheduler): Subscriber[T] = {
    ReactiveSubscriberAsMonifuSubscriber(subscriber)
  }

  /**
   * Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
   * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification.
   */
  def toReactiveSubscriber[T](subscriber: Subscriber[T]): RSubscriber[T] = {
    toReactiveSubscriber(subscriber, subscriber.scheduler.env.batchSize)
  }

  /**
   * Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
   * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
   * specification.
   * 
   * @param bufferSize a strictly positive number, representing the size
   *                   of the buffer used and the number of elements requested
   *                   on each cycle when communicating demand, compliant with
   *                   the reactive streams specification
   */
  def toReactiveSubscriber[T](source: Subscriber[T], bufferSize: Int): RSubscriber[T] = {
    source match {
      case sync: SynchronousSubscriber[_] =>
        val inst = sync.asInstanceOf[SynchronousSubscriber[T]]
        SynchronousSubscriberAsReactiveSubscriber(inst, bufferSize)
      case async =>
        SubscriberAsReactiveSubscriber(async, bufferSize)
    }
  }
  
  /**
   * Extension methods for [[Subscriber]].
   */
  implicit class Extensions[T](val source: Subscriber[T]) extends AnyVal {
    /**
     * Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
     * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
     * specification.
     */
    def toReactive: RSubscriber[T] =
      Subscriber.toReactiveSubscriber(source)

    /**
     * Transforms the source [[Subscriber]] into a `org.reactivestreams.Subscriber`
     * instance as defined by the [[http://www.reactive-streams.org/ Reactive Streams]]
     * specification.
     *
     * @param bufferSize a strictly positive number, representing the size
     *                   of the buffer used and the number of elements requested
     *                   on each cycle when communicating demand, compliant with
     *                   the reactive streams specification
     */
    def toReactive(bufferSize: Int): RSubscriber[T] =
      Subscriber.toReactiveSubscriber(source, bufferSize)

    /**
     * Feeds the source [[Subscriber]] with elements from the given iterable,
     * respecting the contract and returning a `Future[Ack]` with the last
     * acknowledgement given after the last emitted element.
     */
    def feed(iterable: Iterable[T]): Future[Ack] =
      Observer.feed(source, iterable)(source.scheduler)

    /**
     * Feeds the source [[Subscriber]] with elements from the given iterator,
     * respecting the contract and returning a `Future[Ack]` with the last
     * acknowledgement given after the last emitted element.
     */
    def feed(iterator: Iterator[T]): Future[Ack] =
      Observer.feed(source, iterator)(source.scheduler)
  }

  private[this] final class Implementation[-T]
      (private val observer: Observer[T], val scheduler: Scheduler)
    extends Subscriber[T] {

    require(observer != null, "Observer should not be null")
    require(scheduler != null, "Scheduler should not be null")

    def onNext(elem: T): Future[Ack] = observer.onNext(elem)
    def onError(ex: Throwable): Unit = observer.onError(ex)
    def onComplete(): Unit = observer.onComplete()

    override def equals(other: Any): Boolean = other match {
      case that: Implementation[_] =>
        observer == that.observer && scheduler == that.scheduler
      case _ =>
        false
    }

    override def hashCode(): Int = {
      31 * observer.hashCode() + scheduler.hashCode()
    }
  }
}
