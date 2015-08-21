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

import monifu.reactive.Observable

import scala.concurrent.duration.Duration

object BufferSizedOverlapSuite extends BaseOperatorSuite {
  val waitNext = Duration.Zero
  val waitFirst = Duration.Zero

  def observable(sourceCount: Int) = {
    require(sourceCount > 0, "count must be strictly positive")
    if (sourceCount > 1) Some {
      val divBy4 = sourceCount / 4 * 4
      val o = Observable.range(0, divBy4)
        .map(_ % 4)
        .buffer(8,4)
        .map(Observable.fromIterable)
        .flatten

      val count = 8 + (divBy4 - 8) * 2
      val sum = (count / 4) * 6
      Sample(o, count, sum, waitFirst, waitNext)
    }
    else Some {
      val o = Observable.unit(1L).window(2,1).flatten
      Sample(o, 1, 1, waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None
}
