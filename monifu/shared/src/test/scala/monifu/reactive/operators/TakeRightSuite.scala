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

import monifu.reactive.{DummyException, Observable}
import scala.concurrent.duration.Duration.Zero

object TakeRightSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int) =
    if (sourceCount == 1) 9 else (1 until sourceCount * 2).takeRight(sourceCount).sum

  def count(sourceCount: Int) =
    sourceCount

  def observable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.range(1, 10).takeRight(1)
      else
        Observable.range(1, sourceCount * 2).takeRight(sourceCount)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = if (sourceCount == 1)
        createObservableEndingInError(Observable.range(1, 10), ex)
          .takeRight(1)
      else
        createObservableEndingInError(Observable.range(1, sourceCount * 2), ex)
          .takeRight(sourceCount)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}
