package monifu.reactive.api

import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.util.{Success, Try}
import scala.concurrent.duration.Duration

/**
 * Represents the acknowledgement of processing that a consumer
 * sends back upstream on `Observer.onNext`
 */
sealed trait Ack

object Ack {
  /**
   * Acknowledgement of processing that signals upstream that the
   * consumer is interested in receiving more events.
   */
  sealed trait Continue extends Ack

  case object Continue extends Continue with AckIsFuture[Continue] {
    final val IsSuccess = Success(Continue)
    final val value = Some(IsSuccess)
  }

  /**
   * Acknowledgement or processing that signals upstream that the
   * consumer is no longer interested in receiving events.
   */
  sealed trait Done extends Ack

  case object Done extends Done with AckIsFuture[Done] {
    final val IsSuccess = Success(Done)
    final val value = Some(IsSuccess)
  }
}

sealed trait AckIsFuture[T <: Ack] extends Future[T] { self =>
  final val isCompleted = true

  final def ready(atMost: Duration)(implicit permit: CanAwait) = self
  final def result(atMost: Duration)(implicit permit: CanAwait) = value.get.get

  final def onComplete[U](func: (Try[T]) => U)(implicit executor: ExecutionContext): Unit =
    executor.execute(new Runnable {
      def run(): Unit = func(value.get)
    })
}





