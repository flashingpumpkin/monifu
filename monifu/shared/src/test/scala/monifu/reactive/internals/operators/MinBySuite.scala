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
import monifu.reactive.{Observer, Observable}
import scala.concurrent.duration.Duration.Zero
import scala.util.Success

object MinBySuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val o = Observable.range(sourceCount, 0, -1).minBy(x => sourceCount - x)
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.create[Long] { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer
      val source = createObservableEndingInError(Observable.range(sourceCount, 0, -1), ex)
        .minBy(x => sourceCount - x)

      o.onNext(sum(sourceCount)).onComplete {
        case Success(Continue) =>
          source.subscribe(o)
        case _ =>
          ()
      }
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) = 1
  def sum(sourceCount: Int) = sourceCount

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount)
      .minBy(x => if (x == sourceCount-1) throw ex else x)

    Sample(o, 0, 0, Zero, Zero)
  }

  test("empty observable should be empty") { implicit s =>
    val source: Observable[Long] = Observable.empty
    var received = 0
    var wasCompleted = false

    source.minBy(x => 100 - x).unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = { received += 1; Continue }
      def onError(ex: Throwable) = ()
      def onComplete() = { wasCompleted = true }
    })

    assertEquals(received, 0)
    assert(wasCompleted)
  }
}