package com.xebia.slowendpoint

import java.net.InetSocketAddress
import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.io.StreamTcp
import akka.stream.scaladsl._
import akka.stream.scaladsl.FlowGraphImplicits._
import akka.util.ByteString

import scala.util.{Failure, Success}

object ParallelProxy {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ClientAndServer")
    val serverAddress = if (args.isEmpty)
      new InetSocketAddress("127.0.0.1", 6000)
    else
      new InetSocketAddress("127.0.0.1", args(0).toInt)
    server(system, serverAddress)
  }

  def server(system: ActorSystem, serverAddress: InetSocketAddress): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = FlowMaterializer()

    val handler = ForeachSink[StreamTcp.IncomingConnection] { conn =>
      println("Client connected from: " + conn.remoteAddress)
      conn handleWith getFlow()
    }

    val binding = StreamTcp().bind(serverAddress)
    val materializedServer = binding.connections.to(handler).run()

    binding.localAddress(materializedServer).onComplete {
      case Success(address) =>
        println("Server started, listening on: " + address)
      case Failure(e) =>
        println(s"Server could not bind to $serverAddress: ${e.getMessage}")
        system.shutdown()
    }
  }

  val endPointAdres: InetSocketAddress = new InetSocketAddress("127.0.0.1", 11111)

  private val numberOfConnections: Int = 20

  def getFlow()(implicit as: ActorSystem): Flow[ByteString, ByteString] = {
    PartialFlowGraph { implicit b =>
      val balance = Balance[ByteString]
      val merge = Merge[ByteString]
      UndefinedSource("in") ~> balance

      1 to numberOfConnections map { _ =>
        balance ~> StreamTcp().outgoingConnection(endPointAdres).flow ~> merge
      }

      merge ~> UndefinedSink("out")
    } toFlow(UndefinedSource("in"), UndefinedSink("out"))
  }
}