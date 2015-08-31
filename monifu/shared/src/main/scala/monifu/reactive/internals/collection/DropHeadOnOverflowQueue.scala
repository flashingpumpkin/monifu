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

package monifu.reactive.internals.collection

import java.util.ConcurrentModificationException
import monifu.util.math.nextPowerOf2
import scala.reflect.ClassTag

/**
 * An [[EvictingQueue]] implementation that on overflow starts
 * dropping old elements.
 *
 * This implementation is not thread-safe and on the JVM it
 * needs to be synchronized.
 *
 * @param _capacity is the recommended capacity that this queue will support,
 *                  however the actual capacity will be the closest power of 2
 *                  that is bigger than the given number, or a maximum of
 *                  2^30^ (the maximum positive int that can be expressed as
 *                  a power of 2)
 */
private[reactive] final class DropHeadOnOverflowQueue[T : ClassTag] private (_capacity: Int)
  extends EvictingQueue[T] { self =>

  require(_capacity > 1, "minCapacity must be bigger than 1")

  private[this] val maxSize = nextPowerOf2(_capacity + 1)
  private[this] val modulus = maxSize - 1
  def capacity = modulus

  private[this] val array = new Array[T](maxSize)
  // head is incremented by `poll()`, or by `offer()` on overflow
  private[this] var headIdx = 0
  // tail is incremented by `offer()`
  private[this] var tailIdx = 0

  override def isEmpty: Boolean = {
    headIdx == tailIdx
  }

  override def nonEmpty: Boolean = {
    headIdx != tailIdx
  }

  def isAtCapacity: Boolean = {
    size >= modulus
  }

  def offer(elem: T): Int = {
    array(tailIdx) = elem
    tailIdx = (tailIdx + 1) & modulus

    if (tailIdx != headIdx) 0 else {
      // overflow just happened, dropping one by incrementing head
      headIdx = (headIdx + 1) & modulus
      1
    }
  }

  def state = {
    (headIdx, tailIdx, array.toSeq)
  }
  def offerMany(seq: T*): Long = {
    seq.foldLeft(0L)((acc, e) => acc + offer(e))
  }

  @throws(classOf[NoSuchElementException])
  def poll(): T = {
    if (headIdx == tailIdx)
      throw new NoSuchElementException("EvictingQueue is empty")
    else {
      val elem = array(headIdx)
      // incrementing head pointer
      headIdx = (headIdx + 1) & modulus
      elem
    }
  }

  def pollMany(array: Array[T], offset: Int = 0): Int = {
    var arrayIdx = offset
    while (arrayIdx < array.length && headIdx != tailIdx) {
      array(arrayIdx) = self.array(headIdx)
      // incrementing head pointer
      headIdx = (headIdx + 1) & modulus
      arrayIdx += 1
    }

    arrayIdx - offset
  }

  override val hasDefiniteSize: Boolean =
    true

  override def size: Int = {
    if (tailIdx >= headIdx)
      tailIdx - headIdx
    else
      (maxSize - headIdx) + tailIdx
  }

  override def head: T = {
    if (headIdx == tailIdx)
      throw new NoSuchElementException("EvictingQueue is empty")
    else
      array(headIdx)
  }

  override def headOption: Option[T] = {
    try Some(head) catch {
      case _: NoSuchElementException =>
        None
    }
  }

  def iterator: Iterator[T] = {
    new Iterator[T] {
      private[this] var isStarted = false
      private[this] val initialHeadIdx = self.headIdx
      private[this] val initialTailIdx = self.tailIdx
      private[this] var tailIdx = 0
      private[this] var headIdx = 0

      def hasNext: Boolean = {
        if (initialHeadIdx != self.headIdx || initialTailIdx != self.tailIdx)
          throw new ConcurrentModificationException(s"headIdx != $initialHeadIdx || tailIdx != $initialTailIdx")

        if (!isStarted) init()
        headIdx != tailIdx
      }

      def next(): T = {
        if (!isStarted) init()
        if (headIdx == tailIdx)
          throw new NoSuchElementException("EvictingQueue.iterator is empty")
        else {
          val elem = array(headIdx)
          // incrementing head pointer
          headIdx = (headIdx + 1) & modulus
          elem
        }
      }

      private[this] def init(): Unit = {
        isStarted = true
        if (self.headIdx != self.tailIdx) {
          headIdx = initialHeadIdx
          tailIdx = initialTailIdx
        }
      }
    }
  }

  def clear(): Unit = {
    headIdx = 0
    tailIdx = 0
  }

  def length: Int = size
}

private[reactive] object DropHeadOnOverflowQueue {
  /** 
   * Builder for [[DropHeadOnOverflowQueue]]
   * 
   * @param capacity is the recommended capacity that this queue will support,
   *                 however the actual capacity will be the closest power of 2
   *                 that is bigger than the given number, or a maximum of
   *                 2^30^ (the maximum positive int that can be expressed as
   *                 a power of 2)
   */
  def apply[T : ClassTag](capacity: Int): DropHeadOnOverflowQueue[T] = {
    new DropHeadOnOverflowQueue[T](capacity)
  }
}