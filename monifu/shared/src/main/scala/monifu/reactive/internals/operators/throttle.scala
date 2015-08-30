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

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] object throttle {
  /** Implementation for [[Observable.throttleFirst]] */
  def first[T](self: Observable[T], interval: FiniteDuration): Observable[T] =
    Observable.create { downstream =>
      import downstream.{scheduler => s}

      self.onSubscribe(new Observer[T] {
        private[this] val intervalMs = interval.toMillis
        private[this] var nextChange = 0L

        def onNext(elem: T): Future[Ack] = {
          val rightNow = s.currentTimeMillis()
          if (nextChange <= rightNow) {
            nextChange = rightNow + intervalMs
            downstream.onNext(elem)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          downstream.onError(ex)
        }

        def onComplete(): Unit = {
          downstream.onComplete()
        }
      })
    }
}
