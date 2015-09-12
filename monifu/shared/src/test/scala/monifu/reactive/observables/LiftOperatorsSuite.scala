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

import minitest.SimpleTestSuite
import monifu.concurrent.Cancelable
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.OverflowStrategy.Unbounded
import monifu.reactive.channels.{ObservableChannel, PublishChannel}
import monifu.reactive.{Subject, Observable}
import monifu.reactive.subjects.PublishSubject

import scala.util.Success

object LiftOperatorsSuite extends SimpleTestSuite {
  test("ConnectableObservable should work") {
    implicit val s = TestScheduler()
    val o = Observable.unit(1).publish
    val result: ConnectableObservable[Int] = o.sum
    val f = result.asFuture

    result.connect()
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("Subject should work") {
    implicit val s = TestScheduler()
    val result: Subject[Int, Int] = PublishSubject[Int]().sum
    val f = result.asFuture

    result.onNext(1)
    result.onComplete()
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("ObservableChannel should work") {
    implicit val s = TestScheduler()
    val result: ObservableChannel[Int, Int] = PublishChannel[Int](Unbounded).sum
    val f = result.asFuture

    result.pushNext(1)
    result.pushComplete()
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("GroupedObservables should work") {
    implicit val s = TestScheduler()
    val (in,out) = GroupedObservable.broadcast[Int,Int](10, Cancelable())
    val result: GroupedObservable[Int, Int] = out.sum
    val f = result.map(_ + result.key).asFuture

    in.onNext(11)
    in.onComplete()
    s.tick()
    assertEquals(f.value, Some(Success(Some(21))))
  }
}
