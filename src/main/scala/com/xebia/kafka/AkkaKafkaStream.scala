package com.xebia.kafka

import akka.actor._
import akka.stream.FlowMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import kafka.message.MessageAndMetadata
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

object AkkaKafkaStream extends App {
  val Topic = "item-topic"
  val GroupID = "updates"
  val ZookeeperConnect = "localhost:2181"

  implicit val system = ActorSystem("akka-kafka-stream")
  implicit val materializer = FlowMaterializer()

  val kafkaConsumer = KafkaConsumer(Topic, GroupID, ZookeeperConnect)
  val kafkaSource = Source(() ⇒ readMessageFromKafka())
  val result = kafkaSource.map(parseMessage).foreach(println)

  result.onComplete {
    case _ ⇒
      println("Shutting down client")
      system.shutdown()
  }

  private def readMessageFromKafka() = kafkaConsumer.read()

  private def parseMessage(msg: MessageAndMetadata[Long, ByteString]): ItemMessage = {
    val message = msg.message.decodeString("utf-8")
    try {
      message.parseJson.convertTo[ItemMessage](ItemMessage.itemMessageFormat)
    } catch {
      case e: Exception ⇒
        println(s"Exception encountered while trying to convert Kafka message to ItemMessage. Raw message: $message", e)
        ItemMessage.Empty // Hack so the flow keeps working after an exception
    }
  }

}

case class ItemMessage(id: String, title: String, price: String)

object ItemMessage {
  import spray.json.DefaultJsonProtocol._

  implicit val itemMessageFormat = jsonFormat3(ItemMessage.apply)

  def Empty: ItemMessage = ItemMessage("", "", "")
}

