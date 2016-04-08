package com.xebia.flowgraph

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Tcp._
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future

/**
  * Created by pme16860 on 07/04/16.
  */
object SampleIncoming {



  def main(args: Array[String]) {
    implicit val system = ActorSystem("ClientAndServer")
    implicit val materializer = ActorMaterializer()
    val connections: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind("127.0.0.1", 9999)
    connections runForeach { connection =>
      println(s"New connection from: ${connection.remoteAddress}")

      val echo = Flow[ByteString]
        .via(Framing.delimiter(
          ByteString("\n"),
          maximumFrameLength = 256,
          allowTruncation = true))
        .map(_.utf8String)
          .map( data => {
            println(s">$data")
            data
          }
          )
        .map(_ + "!!!\n")
        .map(ByteString(_))

      connection.handleWith(echo)
    }
  }

}
