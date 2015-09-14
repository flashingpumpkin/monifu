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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import minitest.TestSuite
import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.OverflowStrategy.DropNew
import monifu.reactive.exceptions.DummyException
import monifu.reactive.{Observable, Subscriber, Ack, Observer}
import scala.concurrent.{Await, Future, Promise}
import concurrent.duration._

object BufferDropNewConcurrencySuite extends TestSuite[Scheduler] {
  def tearDown(env: Scheduler) = ()
  
  def setup() = {
    monifu.concurrent.Implicits.globalScheduler
  }

  test("merge test should work") { implicit s =>
    val source = Observable.repeat(1L).take(1000000)
    val o1 = source.map(_ + 2)
    val o2 = source.map(_ + 3)
    val o3 = source.map(_ + 4)

    val f = Observable(o1, o2, o3)
      .merge(DropNew(1000))
      .sum
      .asFuture

    val result = Await.result(f, 30.seconds)
    assert(result.exists(_ > 0))
  }

  test("should not lose events, test 1") { implicit s =>
    var number = 0
    val completed = new CountDownLatch(1)

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed.countDown()
      }
    }

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), DropNew(100000))
    for (i <- 0 until 100000) buffer.onNext(i)
    buffer.onComplete()

    assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    assert(number == 100000)
  }

  test("should not lose events, test 2") { implicit s =>
    var number = 0
    val completed = new CountDownLatch(1)

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed.countDown()
      }
    }

    val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), DropNew(100000))

    def loop(n: Int): Unit =
      if (n > 0) s.execute(new Runnable {
        def run() = { buffer.onNext(n); loop(n-1) }
      })
      else buffer.onComplete()

    loop(10000)
    assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    assertEquals(number, 10000)
  }

  test("should drop incoming when over capacity") { implicit s =>
    // repeating test 100 times because of problems
    for (_ <- 0 until 100) {
      var sum = 0
      val completed = new CountDownLatch(1)
      val promise = Promise[Ack]()

      val underlying = new Observer[Int] {
        var received = 0

        def onNext(elem: Int) = {
          sum += elem
          received += 1
          promise.future
        }

        def onError(ex: Throwable): Unit = {
          s.reportFailure(ex)
        }

        def onComplete() = {
          completed.countDown()
        }
      }

      val buffer = BufferedSubscriber[Int](Subscriber(underlying, s), DropNew(5))

      assertEquals(buffer.onNext(1), Continue)
      assertEquals(buffer.onNext(2), Continue)
      assertEquals(buffer.onNext(3), Continue)
      assertEquals(buffer.onNext(4), Continue)
      assertEquals(buffer.onNext(5), Continue)

      for (i <- 0 until 20) buffer.onNext(6 + i)
      buffer.onComplete()
      promise.success(Continue)

      assert(completed.await(5, TimeUnit.SECONDS), "wasCompleted.await should have succeeded")
      assertEquals(sum, 15)
    }
  }

  test("should send onError when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }

      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, DropNew(5))

    buffer.onError(new RuntimeException("dummy"))
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")

    val r = buffer.onNext(1)
    assertEquals(r, Cancel)
  }

  test("should send onError when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = {
        assert(ex.getMessage == "dummy")
        latch.countDown()
      }
      def onNext(elem: Int) = Continue
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, DropNew(5))

    buffer.onNext(1)
    buffer.onError(new RuntimeException("dummy"))
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onError when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()

    val buffer = BufferedSubscriber[Int](
      new Subscriber[Int] {
        def onError(ex: Throwable) = {
          assert(ex.getMessage == "dummy")
          latch.countDown()
        }
        def onNext(elem: Int) = promise.future
        def onComplete() = throw new IllegalStateException()
        val scheduler = s
      }, DropNew(5))

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onNext(5)
    buffer.onError(DummyException("dummy"))

    promise.success(Continue)
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = latch.countDown()
      val scheduler = s
    }, DropNew(5))

    buffer.onComplete()
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = promise.future
      def onComplete() = latch.countDown()
      val scheduler = s
    }, DropNew(5))

    buffer.onNext(1)
    buffer.onComplete()
    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when at capacity") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber[Int](new Subscriber[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = promise.future
      def onComplete() = latch.countDown()
      val scheduler = s
    }, DropNew(5))

    buffer.onNext(1)
    buffer.onNext(2)
    buffer.onNext(3)
    buffer.onNext(4)
    buffer.onComplete()

    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should do onComplete only after all the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue]()

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
      val scheduler = s
    }, DropNew(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onComplete()
    startConsuming.success(Continue)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
      val scheduler = s
    }, DropNew(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onComplete()

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onError only after the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue]()

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, DropNew(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onError(new RuntimeException)
    startConsuming.success(Continue)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onError only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber[Long](new Subscriber[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
      val scheduler = s
    }, DropNew(10000))

    (0 until 9999).foreach(x => buffer.onNext(x))
    buffer.onError(new RuntimeException)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }
}