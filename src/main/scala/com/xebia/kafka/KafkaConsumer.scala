package com.xebia.kafka

import java.util.Properties

import com.xebia.kafka.decoders._
import kafka.consumer.{ Consumer, ConsumerConfig, Whitelist }

import scala.collection.JavaConversions._

class KafkaConsumer(topic: String,
    groupId: String,
    zookeeperConnect: String,
    readFromStartOfStream: Boolean = true) {

  val props = new Properties()
  props.put("group.id", groupId)
  props.put("zookeeper.connect", zookeeperConnect)
  props.put("auto.offset.reset", if (readFromStartOfStream) "smallest" else "largest")

  val config = new ConsumerConfig(props)
  val connector = Consumer.create(config)

  val filterSpec = new Whitelist(topic)

  val stream = connector.createMessageStreamsByFilter(filterSpec, 1, LongDecoder, ByteStringDecoder).get(0)

  def read() = ConsumerIteratorWrapper(stream.iterator())

  def close() {
    connector.shutdown()
  }
}

object KafkaConsumer {
  def apply(topic: String, groupId: String, zookeeperConnect: String): KafkaConsumer = new KafkaConsumer(topic, groupId, zookeeperConnect)
}
