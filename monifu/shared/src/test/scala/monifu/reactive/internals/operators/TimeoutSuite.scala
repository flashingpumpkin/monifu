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

import java.util.concurrent.TimeoutException
import monifu.concurrent.extensions._
import monifu.reactive.Ack.Continue
import monifu.reactive.exceptions.DummyException
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.subjects.PublishSubject
import monifu.reactive.{Observer, Observable}
import scala.concurrent.Future
import scala.concurrent.duration._

object TimeoutSuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val source = Observable.create[Long](_.observer.onNext(sourceCount))
    val o = source.timeout(1.second).onErrorRecoverWith {
      case _: TimeoutException =>
        Observable.unit(20L)
    }

    Sample(o, 2, sourceCount + 20, Duration.Zero, 1.second)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    val ex = DummyException("dummy")
    observable(sourceCount).map(s => s.copy(observable =
      createObservableEndingInError(s.observable, ex)))
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  test("should emit timeout after time passes") { implicit s =>
    val p = PublishSubject[Int]()
    var received = 0
    var errorThrown: Throwable = null

    p.timeout(10.seconds).subscribe(new SynchronousObserver[Int] {
      def onComplete() = ()
      def onError(ex: Throwable) = {
        errorThrown = ex
      }

      def onNext(elem: Int) = {
        received += elem
        Continue
      }
    })

    p.onNext(1)
    assertEquals(received, 1)

    s.tick(9.seconds)
    p.onNext(2)
    assertEquals(received, 3)

    s.tick(9.seconds)
    assertEquals(received, 3)
    assertEquals(errorThrown, null)

    s.tick(1.second)
    assert(errorThrown != null && errorThrown.isInstanceOf[TimeoutException],
      "errorThrown should be a TimeoutException")
  }

  test("should apply back-pressure on timeout") { implicit s =>
    val p = PublishSubject[Int]()
    var received = 0
    var errorThrown: Throwable = null

    p.timeout(10.seconds).subscribe(new Observer[Int] {
      def onComplete() = ()
      def onError(ex: Throwable) = {
        errorThrown = ex
      }

      def onNext(elem: Int) =
        Future.delayedResult(15.second) {
          received += elem
          Continue
        }
    })

    p.onNext(1)
    assertEquals(received, 0)
    s.tick(10.second)

    assertEquals(received, 0)
    assertEquals(errorThrown, null)

    s.tick(5.second)
    assertEquals(received, 1)
    assert(errorThrown != null && errorThrown.isInstanceOf[TimeoutException],
      "errorThrown should be a TimeoutException")
  }
}
