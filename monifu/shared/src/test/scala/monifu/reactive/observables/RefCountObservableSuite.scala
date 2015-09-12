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

package monifu.reactive.observables

import minitest.TestSuite
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.OverflowStrategy.Unbounded
import monifu.reactive.channels.PublishChannel
import monifu.reactive.exceptions.DummyException
import monifu.reactive.{Ack, Observer, Observable}
import concurrent.duration._
import scala.concurrent.Future

object RefCountObservableSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should work") { implicit s =>
    var received = 0L
    var completed = 0

    def createObserver = new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        received += 1
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = completed += 1
    }

    val ref = Observable.interval(2.seconds).publish.refCount
    val s1 = ref.subscribe(createObserver)

    assertEquals(received, 0)
    s.tick(); assertEquals(received, 1)
    s.tick(2.seconds); assertEquals(received, 2)

    val s2 = ref.subscribe(createObserver)
    s.tick(); assertEquals(received, 2)
    s.tick(2.seconds); assertEquals(received, 4)
    s.tick(2.seconds); assertEquals(received, 6)

    s1.cancel()
    s.tick(); assertEquals(received, 6)
    s.tick(2.seconds); assertEquals(received, 7)
    assertEquals(completed, 1)

    s2.cancel()
    s.tick(2.seconds); assertEquals(received, 7)
    assertEquals(completed, 2)
    s.tick(2.seconds)

    ref.subscribe(createObserver)
    s.tick(2.seconds); assertEquals(received, 7)
    assertEquals(completed, 3)

    ref.subscribe(createObserver)
    s.tick(2.seconds); assertEquals(received, 7)
    assertEquals(completed, 4)
  }

  test("onError should stop everything") { implicit s =>
    var received = 0L
    var completed = 0

    def createObserver = new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        received += 1
        Continue
      }

      def onError(ex: Throwable): Unit = completed += 1
      def onComplete(): Unit = ()
    }

    val ch = PublishChannel[Long](Unbounded)
    val ref = ch.publish.refCount
    ref.subscribe(createObserver)
    ref.subscribe(createObserver)

    assertEquals(received, 0)
    ch.pushNext(1)
    s.tick(); assertEquals(received, 2)

    ch.pushError(DummyException("dummy"))
    s.tick(); assertEquals(completed, 2)

    ref.subscribe(createObserver)
    assertEquals(completed, 3)
    ref.subscribe(createObserver)
    assertEquals(completed, 4)
    assertEquals(received, 2)
  }

  test("onComplete") { implicit s =>
    var received = 0L
    var completed = 0

    def createObserver = new Observer[Long] {
      def onNext(elem: Long): Future[Ack] = {
        received += 1
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = completed += 1
    }

    val ch = PublishChannel[Long](Unbounded)
    val ref = ch.publish.refCount
    ref.subscribe(createObserver)
    ref.subscribe(createObserver)

    ch.pushNext(1)
    ch.pushComplete()
    s.tick()

    assertEquals(received, 2)
    assertEquals(completed, 2)
  }
}
