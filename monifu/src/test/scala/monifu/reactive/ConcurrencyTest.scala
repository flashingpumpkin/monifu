package monifu.reactive

import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import scala.concurrent.{Future, Await}
import concurrent.duration._
import scala.util.Random
import monifu.concurrent.extensions._

class ConcurrencyTest extends FunSpec {
  describe("Observable.take") {
    it("should work asynchronously") {
      val obs = Observable.range(0, 10000)
        .subscribeOn(global)
        .observeOn(global).take(9000)
        .observeOn(global)
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(0 until 9000))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.range(0, 10000)
        .observeOn(global)
        .take(9000)
        .flatMap(x => Observable.range(x, x + 100).take(5))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some((0 until 9000).flatMap(x => x until (x + 5)).sum))
    }
  }

  describe("Observable.takeWhile") {
    it("should work asynchronously") {
      val obs = Observable.range(0, 10000)
        .subscribeOn(global)
        .observeOn(global).takeWhile(_ < 9000)
        .observeOn(global)
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(0 until 9000))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.range(0, 10000)
        .observeOn(global)
        .takeWhile(_ < 9000)
        .flatMap(x => Observable.range(x, x + 100).takeWhile(_ < x + 5))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some((0 until 9000).flatMap(x => x until (x + 5)).sum))
    }
  }

  describe("Observable.drop") {
    it("should work asynchronously") {
      val obs = Observable.range(10000, 0, -1)
        .subscribeOn(global)
        .observeOn(global).drop(9900)
        .observeOn(global)
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1)))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.from(10000.until(0, -1))
        .observeOn(global)
        .drop(9900)
        .flatMap(x => Observable.range(x, x + 100).drop(90))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1).flatMap(x => x.until(x + 100).drop(90)).sum))
    }
  }

  describe("Observable.dropWhile") {
    it("should work asynchronously") {
      val obs = Observable.range(10000, 0, -1)
        .subscribeOn(global)
        .observeOn(global).dropWhile(_ > 100)
        .observeOn(global)
        .foldLeft(Seq.empty[Int])(_ :+ _)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1)))
    }

    it("should work with an asynchronous operator") {
      val obs = Observable.from(10000.until(0, -1))
        .observeOn(global)
        .dropWhile(_ > 100)
        .flatMap(x => Observable.range(x, x + 100).dropWhile(_ < x + 90))
        .foldLeft(0)(_+_)

      val r = Await.result(obs.asFuture, 10.seconds)
      assert(r === Some(100.until(0, -1).flatMap(x => x.until(x + 100).drop(90)).sum))
    }
  }

  describe("Observable.interval") {
    it("should not have concurrency problems") {
      val f = Observable.interval(1.millisecond).observeOn(global)
        .take(100)
        .foldLeft(Seq.empty[Long])(_:+_)
        .asFuture

      val list = Await.result(f, 40.seconds)
      assert(list === Some(0 until 100))
    }
  }

  describe("Observable.fromIterable") {
    it("should not have concurrency problems") {
      val f = Observable.from(1 until 1000).observeOn(global)
        .map(_.toLong)
        .take(100)
        .foldLeft(Seq.empty[Long])(_:+_)
        .asFuture

      val list = Await.result(f, 10.seconds)
      assert(list === Some(1 to 100))
    }
  }

  describe("Observable.merge") {
    it("should not have concurrency problems, test 1") {
      val f = Observable.from(0 until 1000)
        .observeOn(global)
        .take(100)
        .observeOn(global)
        .mergeMap(x => Observable.range(x, x + 100).observeOn(global).take(10).mergeMap(x => Observable.unit(x).observeOn(global)))
        .foldLeft(Seq.empty[Int])(_:+_)
        .asFuture

      val r = Await.result(f, 20.seconds)
      assert(r.nonEmpty && r.get.size === 100 * 10)
      assert(r.get.sorted === (0 until 1000).take(100).flatMap(x => x until (x + 10)).sorted)
    }

    it("should not have concurrency problems, test 2") {
      val f = Observable.from(0 until 1000)
        .observeOn(global)
        .take(100)
        .observeOn(global)
        .mergeMap(x => Observable.range(x, x + 100).observeOn(global).take(10).mergeMap(x => Observable.unit(x).observeOn(global)))
        .take(100 * 9)
        .foldLeft(Seq.empty[Int])(_:+_)
        .asFuture

      val r = Await.result(f, 20.seconds)
      assert(r.nonEmpty && r.get.size === 100 * 9)
    }
  }

  describe("Observable.takeRight") {
    it("should not have concurrency problems") {
      val f = Observable.range(0, 10000).observeOn(global).takeRight(100)
        .foldLeft(Seq.empty[Int])(_ :+ _).asFuture

      val r = Await.result(f, 20.seconds)
      assert(r === Some(9900 until 10000))
    }
  }

  describe("Observable.flatScan") {
    it("should not have concurrency problems") {
      def sumUp(x: Long, y: Int) = Future.delayedResult(Random.nextInt(3).millisecond)(x + y)
      val obs = Observable.range(0, 1000).flatScan(0L)(sumUp)
        .foldLeft(Seq.empty[Long])(_ :+ _)

      val f = obs.asFuture
      val result = Await.result(f, 30.seconds).get

      assert(result === (0 until 1000).map(x => (0 to x).sum))
    }
  }
}
