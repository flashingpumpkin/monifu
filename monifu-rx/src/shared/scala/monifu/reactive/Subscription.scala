package monifu.reactive

/**
 * Represents a one-to-one lifecycle of a [[Subscriber]] subscribing to a [[Publisher]]
 * and mirrors the `Subscription` interface from the
 * [[http://www.reactive-streams.org/ Reactive Streams]] specification.
 *
 * It can be used only once by a single [[Subscriber]]. It is used
 * for both signaling demand for data and for canceling demand (and allow
 * resource cleanup).
 */
trait Subscription {
  /**
   * No events will be sent by a [[Publisher]] until demand is signaled via this method.
   *
   * It can be called however often and whenever needed.
   * Whatever has been requested can be sent by the [[Publisher]]
   * so only signal demand for what can be safely handled.
   *
   * A [[Publisher]] can send less than is requested if the stream ends but
   * then must emit either `onError` or `onComplete`.
   *
   * The [[Subscriber]] MAY call this method synchronously in the implementation of its
   * `onSubscribe` / `onNext` methods, therefore the effects of this function must be
   * asynchronous, otherwise it could lead to a stack overflow.
   *
   * @param n signals demand for the number of `onNext` events that the [[Subscriber]] wants,
   *          if positive, then the [[Publisher]] is bound by contract to not send more than
   *          this number of `onNext` events and if negative, then this signals to the
   *          [[Publisher]] that it may send an infinite number of events, until the subscription
   *          gets cancelled or the stream is complete.
   */
  def request(n: Int): Unit

  /**
   * Request the [[Publisher]] to stop sending data and clean up resources.
   *
   * Data may still be sent to meet previously signalled demand after
   * calling cancel as this request is asynchronous.
   */
  def cancel(): Unit
}
