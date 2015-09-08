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

import monifu.reactive.Observable
import scala.concurrent.duration._

object SwitchSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val count = sourceCount / 2 * 2 + 1
    val o = Observable.interval(2.seconds)
      .switchMap(i => Observable.interval(1.second).map(_ => i).take(2) ++ Observable.empty.delaySubscription(1.second))
      .take(count)

    val sum = (0 until count).flatMap(x => Seq(x,x)).take(count).sum
    Sample(o, count, sum, waitFirst, waitNext)
  }

  def waitFirst = 1.seconds
  def waitNext = 1.second

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val count = sourceCount / 2 * 2 + 1
    val o = createObservableEndingInError(Observable.interval(2.seconds).take(count), ex)
      .switchMap(i => Observable.interval(1.second).map(_ => i).take(2) ++ Observable.empty.delaySubscription(1.second))

    val sum = (0 until count).flatMap(x => Seq(x,x)).take(count).sum
    Sample(o, count, sum, waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None
}
