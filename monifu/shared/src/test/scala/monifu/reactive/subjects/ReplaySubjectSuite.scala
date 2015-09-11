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

package monifu.reactive.subjects

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer}
import monifu.reactive.exceptions.DummyException
import monifu.reactive.observers.SynchronousObserver
import scala.concurrent.Future

object ReplaySubjectSuite extends BaseSubjectSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long]) = {
    val s = ReplaySubject[Long]()
    Sample(s, expectedElems.sum)
  }

  def continuousStreamingTest(expectedElems: Seq[Long]) = {
    val s = ReplaySubject[Long]()
    Some(Sample(s, expectedElems.sum))
  }

  test("subscribers should get everything") { implicit s =>
    var completed = 0

    def create(expectedSum: Long) = new SynchronousObserver[Int] {
      var received = 0L
      def onNext(elem: Int) = { received += elem; Continue }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = {
        assertEquals(received, expectedSum)
        completed += 1
      }
    }

    val subject = ReplaySubject[Int]()
    subject.onSubscribe(create(20000))

    s.tick(); subject.onNext(2); s.tick()

    for (_ <- 1 until 5000) assertEquals(subject.onNext(2), Continue)

    subject.onSubscribe(create(20000))
    s.tick(); subject.onNext(2); s.tick()

    for (_ <- 1 until 5000) assertEquals(subject.onNext(2), Continue)

    subject.onSubscribe(create(20000))
    s.tick()

    subject.onComplete()
    s.tick()
    subject.onSubscribe(create(20000))
    s.tick()

    assertEquals(completed, 4)
  }


  test("should work synchronously for synchronous subscribers, but after first onNext") { implicit s =>
    val subject = ReplaySubject[Int]()
    var received = 0
    var wasCompleted = 0

    for (i <- 0 until 10)
      subject.onSubscribe(new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          received += elem
          Continue
        }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    assert(subject.onNext(1) != Continue)
    s.tick()

    assertEquals(subject.onNext(2), Continue)
    assertEquals(subject.onNext(3), Continue)
    subject.onComplete()

    assertEquals(received, 60); s.tick()
    assertEquals(wasCompleted, 10)
  }

  test("should work with asynchronous subscribers") { implicit s =>
    val subject = ReplaySubject[Int]()
    var received = 0
    var wasCompleted = 0

    for (i <- 0 until 10)
      subject.onSubscribe(new Observer[Int] {
        def onNext(elem: Int) = Future {
          received += elem
          Continue
        }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit = wasCompleted += 1
      })

    for (i <- 1 to 10) {
      val ack = subject.onNext(i)
      assert(!ack.isCompleted)
      s.tick()
      assert(ack.isCompleted)
      assertEquals(received, (1 to i).sum * 10)
    }

    subject.onComplete()
    assertEquals(received, 5 * 11 * 10)
    assertEquals(wasCompleted, 10)
  }

  test("subscribe after complete should complete immediately") { implicit s =>
    val subject = ReplaySubject[Int]()
    subject.onComplete()

    var wasCompleted = false
    subject.onSubscribe(new Observer[Int] {
      def onNext(elem: Int) = throw new IllegalStateException("onNext")
      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    assert(wasCompleted)
  }

  test("onError should terminate current and future subscribers") { implicit s =>
    val subject = ReplaySubject[Int]()
    val dummy = DummyException("dummy")
    var elemsReceived = 0
    var errorsReceived = 0

    for (_ <- 0 until 10)
      subject.onSubscribe(new Observer[Int] {
        def onNext(elem: Int) = { elemsReceived += elem; Continue }
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit = ex match {
          case `dummy` => errorsReceived += 1
          case _ => ()
        }
      })

    subject.onNext(1)
    subject.onError(dummy)
    s.tick()

    subject.onSubscribe(new Observer[Int] {
      def onNext(elem: Int) = { elemsReceived += elem; Continue }
      def onComplete(): Unit = ()
      def onError(ex: Throwable): Unit = ex match {
        case `dummy` => errorsReceived += 1
        case _ => ()
      }
    })

    s.tick()
    assertEquals(elemsReceived, 11)
    assertEquals(errorsReceived, 11)
  }
}
