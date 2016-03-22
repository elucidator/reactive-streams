package com.xebia.flowgraph

import java.net.InetSocketAddress

import akka.actor._
import akka.util.ByteString
import java.net.InetSocketAddress

import akka.io.IO
import akka.io.Tcp.Bind
import akka.stream.FlowMaterializer
import akka.stream.io.StreamTcp
import akka.stream.scaladsl._
import akka.stream.scaladsl.FlowGraphImplicits._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}

import scala.util.{Failure, Success}
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.Future

object ParallismWithBackpressureSample extends App {
  val msg = s"Some l${"o" * 400}g message"
  val proxyHost = "localhost"
  val proxyPort = 6000
  val endpointHost = "localhost"
  val endpointPort = 11111
  val messageCount = 50000
  val delay = Option(1000)
  val numberOfConnections = 2

  val sys1 = LatencyEndpointServer.init(endpointHost, endpointPort, delay)
  val sys2 = ParallelProxyServer.init(proxyHost, proxyPort, endpointHost, endpointPort, numberOfConnections)
  BlockingSocketClient.run(proxyHost, proxyPort, msg, messageCount)
  sys1.shutdown()
  sys2.shutdown()

}

object ParallelProxyServer {

  def init(serverHost: String = "localhost", serverPort: Int, endpointHost: String, endpointPort: Int, numberOfConnections: Int): ActorSystem = {
    implicit val system = ActorSystem("parallel-proxy")
    new ParallelProxyServer(new InetSocketAddress(serverHost, serverPort), new InetSocketAddress(endpointHost, endpointPort), numberOfConnections).init()
    system
  }
}
class ParallelProxyServer(serverAddress: InetSocketAddress, endPointAdres: InetSocketAddress, val numberOfConnections: Int)(implicit system: ActorSystem) {

  println(s"=========== Number of endpoint connections: $numberOfConnections  ============")
  import system.dispatcher

  def init() = {
    implicit val materializer = ActorMaterializer()
    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println("Client connected from: " + conn.remoteAddress)
      //one-to-one
      //conn handleWith(StreamTcp().outgoingConnection(endPointAdres).flow) 
      
      //fan-out
      conn handleWith parallelFlow()
    }

    val binding: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind(serverAddress.getHostName, serverAddress.getPort)

    val materializedServer = binding.to(handler).run()

    binding.(materializedServer).onComplete {
      case Success(address) =>
        println("Server started, listening on: " + address)
      case Failure(e) =>
        println(s"Server could not bind to $serverAddress: ${e.getMessage}")
        system.shutdown()
    }
  }

  private def parallelFlow(): Flow[ByteString, ByteString, Nothing] = {
    PartialFlowGraph { implicit b =>
      val balance = Balance[ByteString]
      val merge = Merge[ByteString]
      UndefinedSource("in") ~> balance

      1 to numberOfConnections map { _ =>
        balance ~> StreamTcp().outgoingConnection(endPointAdres).flow ~> merge
      }

      merge ~> UndefinedSink("out")
    } toFlow (UndefinedSource("in"), UndefinedSink("out"))
  }

}


