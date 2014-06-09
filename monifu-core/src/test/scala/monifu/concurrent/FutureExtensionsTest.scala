package monifu.concurrent

import org.scalatest.FunSuite
import scala.concurrent.{Await, Future}
import concurrent.duration._
import java.util.concurrent.TimeoutException
import monifu.concurrent.extensions._

class FutureExtensionsTest extends FunSuite {
  import Scheduler.Implicits.global

  test("delayedResult") {
    val startedAt = System.nanoTime()
    val f = Future.delayedResult(100.millis)("TICK")

    assert(Await.result(f, 10.seconds) === "TICK")
    assert((System.nanoTime() - startedAt).nanos >= 100.millis)
  }

  test("withTimeout should succeed") {
    val f = Future.delayedResult(50.millis)("Hello world!")
    val t = f.withTimeout(300.millis)

    assert(Await.result(t, 10.seconds) === "Hello world!")
  }

  test("withTimeout should fail") {
    val f = Future.delayedResult(1.second)("Hello world!")
    val t = f.withTimeout(30.millis)

    intercept[TimeoutException] {
      Await.result(t, 10.seconds)
    }
  }

  test("ensureDuration should succeed on lower time bound") {
    val start = System.nanoTime()
    val f = Future(1).ensureDuration(400.millis)
    assert(Await.result(f, 10.seconds) === 1)

    val duration = (System.nanoTime() - start).nanos
    assert(duration >= 400.millis, s"$duration < 400 millis")
  }

  test("ensureDuration should succeed on lower bound, with upper bound specified") {
    val start = System.nanoTime()
    val f = Future(1).ensureDuration(400.millis, 10.seconds)
    assert(Await.result(f, 10.seconds) === 1)

    val duration = (System.nanoTime() - start).nanos
    assert(duration >= 400.millis, s"$duration < 400 millis")
  }

  test("ensureDuration should succeed on upper bound") {
    val start = System.nanoTime()
    val f = Future.delayedResult(1.second)(1)
      .ensureDuration(10.millis, 200.millis)

    intercept[TimeoutException] {
      Await.result(f, 10.seconds)
    }

    val duration = (System.nanoTime() - start).nanos
    assert(duration >= 200.millis, s"$duration < 200 millis")
  }
}
