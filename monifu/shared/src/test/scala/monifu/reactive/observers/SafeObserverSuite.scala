/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.observers

import minitest.TestSuite
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, DummyException, Observer}
import scala.concurrent.{Future, Promise}
import scala.util.Success


object SafeObserverSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty)
  }

  test("should protect against synchronous errors, test 1") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        throw new DummyException
      }

      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
      }
    })

    val r = observer.onNext(1)
    assertEquals(r, Cancel)
    assert(errorThrown.isInstanceOf[DummyException])

    val r2 = observer.onNext(1)
    assertEquals(r2, Cancel)
    assert(s.state.get.lastReportedError == null)
  }

  test("should protect against synchronous errors, test 2") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        Future.failed(new DummyException)
      }

      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
      }
    })

    val r = observer.onNext(1)
    assertEquals(r, Cancel)
    assert(errorThrown.isInstanceOf[DummyException])

    val r2 = observer.onNext(1)
    assertEquals(r2, Cancel)
    assert(s.state.get.lastReportedError == null)
  }

  test("should protect against asynchronous errors") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        Future { throw new DummyException }
      }

      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
      }
    })

    val r = observer.onNext(1)
    s.tick()

    assertEquals(r.value, Some(Success(Cancel)))
    assert(errorThrown.isInstanceOf[DummyException])

    val r2 = observer.onNext(1); s.tick()
    assertEquals(r2.value, Some(Success(Cancel)))
    assert(s.state.get.lastReportedError == null)
  }

  test("should protect against errors in onComplete") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int) = Continue
      def onComplete(): Unit = {
        throw new DummyException()
      }

      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
      }
    })

    observer.onComplete()
    assert(errorThrown.isInstanceOf[DummyException])

    observer.onComplete()
    assert(s.state.get.lastReportedError == null)
  }

  test("should protect against errors in onError") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int) = Continue
      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
        throw new DummyException("internal")
      }
    })

    observer.onError(new DummyException("external"))
    assertEquals(errorThrown, DummyException("external"))
    assertEquals(s.state.get.lastReportedError, DummyException("internal"))

    observer.onError(new DummyException("external 2"))
    assertEquals(errorThrown, DummyException("external"))
  }

  test("should protect against total collapse") { implicit s =>
    var errorThrown: Throwable = null
    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int) = Continue
      def onComplete(): Unit = {
        throw new DummyException("onComplete")
      }
      def onError(ex: Throwable): Unit = {
        assert(errorThrown == null)
        errorThrown = ex
        throw new DummyException("onError")
      }
    })

    observer.onComplete()
    assertEquals(errorThrown, DummyException("onComplete"))
    assertEquals(s.state.get.lastReportedError, DummyException("onError"))
  }

  test("on synchronous cancel should block further signals") { implicit s =>
    var received = 0
    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int) = {
        received += 1
        Cancel
      }
      def onComplete(): Unit = {
        received += 1
      }
      def onError(ex: Throwable): Unit = {
        received += 1
      }
    })

    assertEquals(observer.onNext(1), Cancel)
    assertEquals(received, 1)
    assertEquals(observer.onNext(1), Cancel)
    assertEquals(received, 1)
    observer.onComplete()
    assertEquals(received, 1)
    observer.onError(DummyException("external"))
    assertEquals(received, 1)
  }

  test("on asynchronous cancel should block further signals") { implicit s =>
    val p = Promise[Cancel]()
    var received = 0

    val observer = SafeObserver(new Observer[Int] {
      def onNext(elem: Int) = {
        received += 1
        p.future
      }

      def onComplete(): Unit = {
        received += 1
      }

      def onError(ex: Throwable): Unit = {
        received += 1
      }
    })

    val r = observer.onNext(1)
    assertEquals(r.value, None)
    s.tick()

    p.success(Cancel)
    s.tick()

    assertEquals(r.value, Some(Success(Cancel)))
    assertEquals(received, 1)

    observer.onComplete()
    observer.onError(new DummyException)
    assertEquals(received, 1)
  }
}
