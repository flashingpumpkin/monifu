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

package monifu.reactive.internals.operators

import monifu.concurrent.cancelables.BooleanCancelable
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.SynchronousSubscriber
import monifu.reactive.{Ack, Observable, Observer, Subscriber}
import scala.concurrent.Future
import scala.concurrent.duration._

private[reactive] object sample {
  /**
   * Implementation for `Observable.sample(initialDelay, delay)`.
   */
  def once[T](source: Observable[T], initialDelay: FiniteDuration, delay: FiniteDuration): Observable[T] =
    once(source, Observable.intervalAtFixedRate(initialDelay, delay))

  /**
   * Implementation for `Observable.sample(sampler)`.
   */
  def once[T,U](source: Observable[T], sampler: Observable[U]): Observable[T] =
    Observable.create { subscriber =>
      source.onSubscribe(new SampleObserver(
        subscriber, sampler, shouldRepeatOnSilence = false))
    }

  /**
   * Implementation for `Observable.sampleRepeated(sampler)`.
   */
  def repeated[T,U](source: Observable[T], sampler: Observable[U]): Observable[T] =
    Observable.create { subscriber =>
      source.onSubscribe(new SampleObserver(
        subscriber, sampler, shouldRepeatOnSilence = true))
    }

  /**
   * Implementation for `Observable.sampleRepeated(initialDelay, delay)`.
   */
  def repeated[T](source: Observable[T], initialDelay: FiniteDuration, delay: FiniteDuration): Observable[T] =
    repeated(source, Observable.intervalAtFixedRate(initialDelay, delay))

  private class SampleObserver[T,U]
  (downstream: Subscriber[T], sampler: Observable[U], shouldRepeatOnSilence: Boolean)
    extends SynchronousSubscriber[T] {

    implicit val scheduler = downstream.scheduler

    @volatile private[this] var hasValue = false
    // MUST BE written before `hasValue = true`
    private[this] var lastValue: T = _
    // to be written in onComplete/onError, to be read from tick
    @volatile private[this] var upstreamIsDone = false
    // MUST BE written to before `upstreamIsDone = true`
    private[this] var upstreamError: Throwable = null
    // MUST BE canceled by the sampler
    private[this] val downstreamConnection = BooleanCancelable()

    def onNext(elem: T): Ack =
      if (downstreamConnection.isCanceled) Cancel else {
        lastValue = elem
        hasValue = true
        Continue
      }

    def onError(ex: Throwable): Unit = {
      upstreamError = ex
      upstreamIsDone = true
    }

    def onComplete(): Unit = {
      upstreamIsDone = true
    }

    sampler.onSubscribe(new Observer[U] {
      private[this] var samplerIsDone = false

      def onNext(elem: U): Future[Ack] = {
        if (samplerIsDone) Cancel else {
          if (upstreamIsDone)
            signalComplete(upstreamError)
          else if (!hasValue)
            Continue
          else {
            hasValue = shouldRepeatOnSilence
            val ack = downstream.onNext(lastValue)
            notifyUpstreamOnCancel(ack, downstreamConnection)
            ack
          }
        }
      }

      def onError(ex: Throwable): Unit = {
        signalComplete(ex)
      }

      def onComplete(): Unit = {
        signalComplete()
      }

      private def signalComplete(ex: Throwable = null): Cancel = {
        if (!samplerIsDone) {
          samplerIsDone = true
          if (ex != null) downstream.onError(ex) else
            downstream.onComplete()
        }

        Cancel
      }

      private def notifyUpstreamOnCancel(ack: Future[Ack], c: BooleanCancelable): Unit = {
        if (ack.isCompleted) {
          if (ack != Continue && ack.value.get != Continue.IsSuccess)
            c.cancel()
        }
        else ack.onComplete {
          case Continue.IsSuccess => ()
          case _ => c.cancel()
        }
      }
    })
  }
}
