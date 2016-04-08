package com.xebia.flowgraph

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ActorMaterializer, SourceShape}
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.actor.{ActorSystem, Cancellable}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.{Await, Future, duration}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by pme16860 on 07/04/16.
  */
object LatencyLineEndpointReceiver {

  def init(host: String = "localhost", port: Int = 11111, delay: Option[Int]): ActorSystem = {
    println(s"============ Delay is: ${delay.map(_.toString).getOrElse("No Delay")}")

    val system = ActorSystem("latency-end-point-receiver")
    val listenTo: InetSocketAddress = new InetSocketAddress(host, port)
    server(system, host, port, delay.get)
    system

  }


  def delayed(system:ActorSystem): ByteString = {
    ByteString.fromString("ACK\n")
    implicit val sys = system
    import scala.concurrent.ExecutionContext.Implicits.global
//    val delayed = akka.pattern.after(200 millis, using = system.scheduler)(Future.failed(
//      new IllegalStateException("OHNOES")))
    val future = Future { Thread.sleep(5); "ACK 1\n" }
//    val result: Future[String] = Future firstCompletedOf Seq(future, delayed)
    val f: String = Await.result(future, Duration(100, "millis"))
    ByteString.fromString(f)
  }



  def server(system: ActorSystem, address: String, port: Int, delay: Int): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val ticks = Source.tick(0.seconds, 10.milliseconds, ())



    val averages = Flow[ByteString]
      .via(Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = Int.MaxValue,
        allowTruncation = true))
      //TODO Some random delay here

      .map(b => {
      //          println(s"Received ${b.length} bytes")
      //        Thread.sleep(100)
      //        delayed(system)
      ByteString("ACK\n")
    })

    val flow: Source[ByteString, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val zip = builder.add(ZipWith((av: ByteString, tick: Unit) => av))

      averages ~> zip.in0
      ticks ~> zip.in1

      SourceShape(zip.out)
    }

    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println("Client connected from: " + conn.remoteAddress)





      conn handleWith flow
    }

    val connections = Tcp().bind(address, port)
    val binding = connections.to(handler).run()

    binding.onComplete {
      case Success(b) =>
        println("Server started, listening on: " + b.localAddress)
      case Failure(e) =>
        println(s"Server could not bind to $address:$port: ${e.getMessage}")
        system.shutdown()
    }

  }

}
