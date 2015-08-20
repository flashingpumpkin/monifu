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

package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{DummyException, Observer, Observable}
import scala.concurrent.duration._

object BufferTimedSuite extends BaseOperatorSuite {
  val waitFirst = 1.second
  val waitNext = 1.second

  def sum(count: Int) = {
    val total = (count * 10 - 1).toLong
    total * (total + 1) / 2
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def observable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    Some {
      val o = Observable.intervalAtFixedRate(100.millis)
        .take(sourceCount * 10)
        .bufferTimed(1.second)
        .map(_.sum)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    Some {
      val o = Observable.intervalAtFixedRate(100.millis)
        .map(x => if (x == sourceCount * 10 - 1) throw ex else x)
        .take(sourceCount * 10)
        .bufferTimed(1.second)
        .map(_.sum)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  test("should emit buffer onComplete") { implicit s =>
    val count = 157

    val obs = Observable.intervalAtFixedRate(100.millis)
      .take(count * 10)
      .bufferTimed(2.seconds)
      .map(_.sum)

    var wasCompleted = false
    var received = 0
    var total = 0L

    obs.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    s.tick(waitNext + waitFirst * (count - 1))
    assertEquals(received, count / 2 + 1)
    assertEquals(total, sum(count))
    s.tick(waitFirst)
    assert(wasCompleted)
  }

  test("should emit buffer onError") { implicit s =>
    val count = 157

    val obs =
      createObservableEndingInError(
        Observable.intervalAtFixedRate(100.millis).take(count * 10),
        DummyException("dummy"))
      .bufferTimed(2.seconds)
      .map(_.sum)

    var errorThrown: Throwable = null
    var received = 0
    var total = 0L

    obs.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = errorThrown = ex
      def onComplete(): Unit = ()
    })

    s.tick(waitNext + waitFirst * (count - 1))
    assertEquals(received, count / 2 + 1)
    assertEquals(total, sum(count))
    s.tick(waitFirst)
    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should throw on negative timespan") { implicit s =>
    intercept[IllegalArgumentException] {
      Observable.intervalAtFixedRate(100.millis)
        .bufferTimed(Duration.Zero - 1.second)
    }
  }
}
