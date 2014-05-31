package monifu.reactive.subjects

import scala.concurrent.Future
import monifu.reactive.api.Ack.{Continue, Done}
import monifu.concurrent.Scheduler
import monifu.reactive.Observer
import monifu.concurrent.atomic.padded.Atomic
import scala.annotation.tailrec
import monifu.reactive.api.Ack
import monifu.reactive.observers.ConnectableObserver


/**
 * `BehaviorSubject` when subscribed, will emit the most recently emitted item by the source,
 * or the `initialValue` (as the seed) in case no value has yet been emitted, the continuing
 * to emit events subsequent to the time of invocation.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.BehaviorSubject.png" />
 *
 * When the source terminates in error, the `BehaviorSubject` will not emit any items to
 * subsequent subscribers, but instead it will pass along the error notification.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.BehaviorSubject.png" />
 */
final class BehaviorSubject[T] private (initialValue: T, s: Scheduler) extends Subject[T,T] { self =>
  import BehaviorSubject.State
  import BehaviorSubject.State._

  implicit val scheduler = s
  private[this] val state = Atomic(Empty(initialValue) : State[T])

  def unsafeSubscribe(observer: Observer[T]): Unit = {
    @tailrec
    def loop(): ConnectableObserver[T] = {
      state.get match {
        case current @ Empty(cachedValue) =>
          val obs = new ConnectableObserver[T](observer)
          obs.scheduleFirst(cachedValue)

          if (!state.compareAndSet(current, Active(Array(obs), cachedValue)))
            loop()
          else
            obs

        case current @ Active(observers, cachedValue) =>
          val obs = new ConnectableObserver[T](observer)
          obs.scheduleFirst(cachedValue)

          if (!state.compareAndSet(current, Active(observers :+ obs, cachedValue)))
            loop()
          else
            obs

        case current @ Complete(cachedValue, errorThrown) =>
          val obs = new ConnectableObserver[T](observer)
          if (errorThrown eq null) {
            obs.scheduleFirst(cachedValue)
            obs.scheduleComplete()
          }
          else {
            obs.schedulerError(errorThrown)
          }
          obs
      }
    }

    loop().connect()
  }

  private[this] def emitNext(obs: Observer[T], elem: T): Future[Continue] =
    obs.onNext(elem) match {
      case Continue => Continue
      case Done =>
        removeSubscription(obs)
        Continue
      case other =>
        other.map {
          case Continue => Continue
          case Done =>
            removeSubscription(obs)
            Continue
        }
    }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    state.get match {
      case current @ Empty(_) =>
        if (!state.compareAndSet(current, Empty(elem)))
          onNext(elem)
        else
          Continue

      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Active(observers, elem)))
          onNext(elem)

        else {
          var idx = 0
          var acc = Continue : Future[Continue]

          while (idx < observers.length) {
            val obs = observers(idx)
            acc =
              if (acc == Continue || (acc.isCompleted && acc.value.get.isSuccess))
                emitNext(obs, elem)
              else {
                val f = emitNext(obs, elem)
                acc.flatMap(_ => f)
              }

            idx += 1
          }

          acc
        }

      case _ =>
        Done
    }
  }

  @tailrec
  def onComplete(): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null))) {
          onComplete() // retry
        }
      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null))) {
          onComplete() // retry
        }
        else {
          var idx = 0
          while (idx < observers.length) {
            observers(idx).onComplete()
            idx += 1
          }
        }
      case _ =>
        // already complete, ignore
    }

  @tailrec
  def onError(ex: Throwable): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex))) {
          onError(ex) // retry
        }
      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex))) {
          onError(ex) // retry
        }
        else {
          var idx = 0
          while (idx < observers.length) {
            observers(idx).onError(ex)
            idx += 1
          }
        }
      case _ =>
        // already complete, ignore
    }

  private[this] def removeSubscription(obs: Observer[T]): Unit =
    state.transform {
      case current @ Active(observers,_) =>
        current.copy(observers.filterNot(_ eq obs))
      case other =>
        other
    }
}

object BehaviorSubject {
  def apply[T](initialValue: T)(implicit scheduler: Scheduler): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue, scheduler)

  private sealed trait State[T]
  private object State {
    case class Empty[T](cachedValue: T) extends State[T]
    case class Active[T](iterator: Array[ConnectableObserver[T]], cachedValue: T) extends State[T]
    case class Complete[T](cachedValue: T, errorThrown: Throwable = null) extends State[T]
  }
}