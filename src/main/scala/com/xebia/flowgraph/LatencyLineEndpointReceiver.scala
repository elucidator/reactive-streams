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
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString

import scala.collection.immutable.Iterable
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


    var cnt = 0;
    val acking = Flow[ByteString]
      .via(Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = Int.MaxValue,
        allowTruncation = true))
      .throttle(1, 10.millis, 1, ThrottleMode.shaping)
      .map(b => {
//        cnt+=1
//        if (cnt % 20 == 0)
//                println(s"Received $cnt messages")
      ByteString("ACK\n")
    })

//      .throttle(1, 100.millis, 1, ThrottleMode.shaping)
//      .map(b => {
//        println(s"delayed ${Thread.currentThread().getId}")
//        b
//      })



    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println("Client connected from: " + conn.remoteAddress)





      conn handleWith acking
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
