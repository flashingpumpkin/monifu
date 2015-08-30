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

package monifu.reactive.internals.builders

import minitest.TestSuite
import monifu.concurrent.extensions._
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.exceptions.DummyException
import monifu.reactive.{Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._

object FromIterableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should be left with no pending tasks")
  }

  test("first execution is async") { implicit s =>
    var wasCompleted = false
    var sum = 0

    Observable.fromIterable(Seq(1,2,3,4,5)).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum += elem
          Continue
        }

        def onComplete(): Unit = wasCompleted = true
        def onError(ex: Throwable): Unit = ()
      })

    assert(!wasCompleted)
    assertEquals(sum, 0)

    assert(s.tickOne())
    assert(wasCompleted)
    assertEquals(sum, 15)
  }

  test("should do synchronous execution in batches") { implicit s =>
    var wasCompleted = false
    var sum = 0

    Observable.fromIterable(0 until (s.env.batchSize * 2)).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum += 1
          Continue
        }

        def onComplete(): Unit = wasCompleted = true
        def onError(ex: Throwable): Unit = ()
      })

    assert(!wasCompleted)
    assertEquals(sum, 0)

    assert(s.tickOne())
    assertEquals(sum, s.env.batchSize)
    assert(s.tickOne())
    assertEquals(sum, s.env.batchSize * 2)
    s.tickOne()
    assert(wasCompleted)
  }

  test("fromIterable should do back-pressure") { implicit s =>
    var wasCompleted = false
    var sum = 0

    Observable.fromIterable(Seq(1,2,3,4,5)).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) =
          Future.delayedResult(100.millis) {
            sum += elem
            Continue
          }

        def onComplete(): Unit = wasCompleted = true
        def onError(ex: Throwable): Unit = ()
      })

    s.tick(50.millis); assertEquals(sum, 0)
    s.tick(50.millis); assertEquals(sum, 1)
    s.tick(50.millis); assertEquals(sum, 1)
    s.tick(50.millis); assertEquals(sum, 3)
    s.tick(50.millis); assertEquals(sum, 3)
    s.tick(50.millis); assertEquals(sum, 6)
    s.tick(50.millis); assertEquals(sum, 6)
    s.tick(50.millis); assertEquals(sum, 10)

    assert(!wasCompleted)
    s.tick(50.millis); assertEquals(sum, 10)
    assert(!wasCompleted)
    s.tick(50.millis); assertEquals(sum, 15)
    assert(wasCompleted)
  }

  test("fromIterable should do empty iterables synchronously") { implicit s =>
    var wasCompleted = false

    Observable.fromIterable(Seq.empty).onSubscribe(
      new Observer[Int] {
        def onComplete(): Unit = wasCompleted = true
        def onNext(elem: Int) = throw new IllegalStateException()
        def onError(ex: Throwable): Unit = ()
      })

    assert(wasCompleted)
  }

  test("fromIterable should stop streaming on Cancel") { implicit s =>
    var wasCompleted = false
    var sum = 0

    Observable.fromIterable(Seq(1,2,3,4,5)).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum += elem
          if (elem == 3) Cancel else Continue
        }

        def onComplete(): Unit = wasCompleted = true
        def onError(ex: Throwable): Unit = ()
      })

    assertEquals(sum, 0)

    s.tick()
    assertEquals(sum, 6)
    assert(!wasCompleted)
  }

  test("fromIterable should protect against broken iterable.next, synchronous version") { implicit s =>
    var sum = 0
    var errorThrown: Throwable = null

    val iterable = new Iterable[Int] {
      def iterator = new Iterator[Int] {
        var counter = 0
        def hasNext = counter < 10
        def next() = {
          if (counter < 3) {
            counter += 1
            counter
          } else {
            throw DummyException("dummy")
          }
        }
      }
    }

    Observable.fromIterable(iterable).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum += elem
          Continue
        }

        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = throw new IllegalStateException()
      })

    s.tick()
    assertEquals(sum, 6)
    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("fromIterable should protect against broken iterable.next, asynchronous version") { implicit s =>
    var sum = 0
    var errorThrown: Throwable = null

    val iterable = new Iterable[Int] {
      def iterator = new Iterator[Int] {
        var counter = 0
        def hasNext = counter < 10
        def next() = {
          if (counter < 3) {
            counter += 1
            counter
          } else {
            throw DummyException("dummy")
          }
        }
      }
    }

    Observable.fromIterable(iterable).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum += elem
          Future.delayedResult(100.millis)(Continue)
        }

        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = throw new IllegalStateException()
      })

    s.tick(10.seconds)
    assertEquals(sum, 6)
    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("fromIterable should protect against broken iterable.hasNext, synchronous version") { implicit s =>
    var sum = 0
    var errorThrown: Throwable = null

    val iterable = new Iterable[Int] {
      def iterator = new Iterator[Int] {
        var counter = 0
        def hasNext = if (counter >= 3) throw DummyException("dummy") else true
        def next() = {
          counter += 1
          counter
        }
      }
    }

    Observable.fromIterable(iterable).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum += elem
          Continue
        }

        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = throw new IllegalStateException()
      })

    s.tick()
    assertEquals(sum, 6)
    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("fromIterable should protect against broken iterable.hasNext, asynchronous version") { implicit s =>
    var sum = 0
    var errorThrown: Throwable = null

    val iterable = new Iterable[Int] {
      def iterator = new Iterator[Int] {
        var counter = 0
        def hasNext = if (counter >= 3) throw DummyException("dummy") else true
        def next() = {
          counter += 1
          counter
        }
      }
    }

    Observable.fromIterable(iterable).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum += elem
          Future.delayedResult(100.millis)(Continue)
        }

        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = throw new IllegalStateException()
      })

    s.tick(10.seconds)
    assertEquals(sum, 6)
    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("fromIterable should protect against broken iterable.hasNext, when iterable is empty") { implicit s =>
    var errorThrown: Throwable = null

    val iterable = new Iterable[Int] {
      def iterator = new Iterator[Int] {
        def hasNext = throw DummyException("dummy")
        def next() = 1
      }
    }

    Observable.fromIterable(iterable).onSubscribe(
      new Observer[Int] {
        def onNext(elem: Int) = {
          Future.delayedResult(100.millis)(Continue)
        }

        def onError(ex: Throwable): Unit = errorThrown = ex
        def onComplete(): Unit = throw new IllegalStateException()
      })

    s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
  }
}
