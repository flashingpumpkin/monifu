/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

import minitest.TestSuite
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.{DummyException, Observer, Observable}

object MiscCompleteSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should complete") { implicit s =>
    var received = 0
    var wasCompleted = false

    Observable.unit(1).complete.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    assertEquals(received, 0)
    assert(wasCompleted)
  }

  test("should signal error") { implicit s =>
    var thrown: Throwable = null

    Observable.error(DummyException("dummy")).complete.unsafeSubscribe(
      new Observer[Long] {
        def onError(ex: Throwable) = thrown = ex
        def onComplete() = ()
        def onNext(elem: Long) = Continue
      })

    assertEquals(thrown, DummyException("dummy"))
  }
}
