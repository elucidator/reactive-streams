package com.xebia.slowendpoint

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, FlowShape}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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
    implicit val materializer = ActorMaterializer()

    val handler = Sink.foreach[Tcp.IncomingConnection] { conn ⇒
      println("Client connected from: " + conn.remoteAddress)
      conn handleWith getFlow()
    }

    val binding = Tcp().bind(serverAddress.getHostString, serverAddress.getPort)
    val materializedServer: Future[ServerBinding] = binding.to(handler).run()

    materializedServer.onComplete {
      case Success(serverBinding) ⇒
        println("Server started, listening on: " + serverBinding.localAddress)
      case Failure(e) ⇒
        println(s"Server could not bind to $serverAddress: ${e.getMessage}")
        system.terminate()
    }
  }

  val endPointAdres: InetSocketAddress = new InetSocketAddress("127.0.0.1", 11111)

  private val numberOfConnections: Int = 20

  def getFlow()(implicit as: ActorSystem) = {
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      val balance = b.add(Balance[ByteString](numberOfConnections))
      val merge = b.add(Merge[ByteString](numberOfConnections))

      1 to numberOfConnections map { _ ⇒
        balance ~> Tcp().outgoingConnection(endPointAdres) ~> merge
      }

      FlowShape(balance.in, merge.out)
    })
  }
}