package com.xebia.kafka

import kafka.consumer.ConsumerIterator
import kafka.message.MessageAndMetadata

/**
 * Wrapper for the standard consumer iterator supplied by Kafka.
 *
 * Safly, the standard Kafka iterator considers a message as read
 * as soon as it is returned, and marks the offset as delivered.
 * This means that during a crash or with unexpected termination, the
 * message will be lost. Because Kafka is closed to extension, we hack
 * around this behaviour with a wrapper.
 *
 * The wrapper causes a message to be marked as read upon the next
 * invocation of either `hasNext` or `next()`.
 *
 * @param wrappedIterator the iterator to wrap
 * @tparam K  the type of the message key
 * @tparam V  the type of the message value
 */
class ConsumerIteratorWrapper[K, V] private (wrappedIterator: ConsumerIterator[K, V])
    extends Iterator[MessageAndMetadata[K, V]] {

  // True if there is a message in flight that hasn't been consumed from the
  // underlying iterator.
  private[this] var messageInFlight = false

  private[this] def consumeInFlightMessage(): Unit = {
    if (messageInFlight) {
      wrappedIterator.next()
      messageInFlight = false
    }
  }

  override def hasNext: Boolean = {
    consumeInFlightMessage()
    wrappedIterator.hasNext()
  }

  override def next(): MessageAndMetadata[K, V] = {
    consumeInFlightMessage()
    messageInFlight = true
    wrappedIterator.peek()
  }
}

object ConsumerIteratorWrapper {
  def apply[K, V](wrapIterator: ConsumerIterator[K, V]) = new ConsumerIteratorWrapper[K, V](wrapIterator)
}
