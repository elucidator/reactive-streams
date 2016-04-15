package com.xebia.flowgraph

import java.net.InetSocketAddress
import java.time.LocalDateTime

import akka.{Done, NotUsed}
import akka.actor._
import akka.io.TcpMessage
import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, OutgoingConnection, ServerBinding}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationConversions, FiniteDuration}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object Counter {
  var total = 0
  var start = 0l
  var stop = 0l
  var count = 0
}

object ParallismWithBackpressureSample extends App {
  val msg = s"Some l${"o" * 400}g message"
  val proxyHost = "localhost"
  val proxyPort = 6003
  val endpointHost = "localhost"
  val endpointPort = 11111
  val messageCount = 1000
  val delay = Option(1000)
  val numberOfConnections = 1


  //val sys1: ActorSystem = LatencyEndpointReceiver.init(endpointHost, endpointPort, delay)
  val sys1: ActorSystem = LatencyLineEndpointReceiver.init(endpointHost, endpointPort, delay)
  val sys2: ActorSystem = ParallelProxyServer.init(proxyHost, proxyPort, endpointHost, endpointPort, numberOfConnections)
  val start = System.currentTimeMillis()
  Counter.total = messageCount
  Counter.start = System.currentTimeMillis()
  BlockingSocketClient.run(proxyHost, proxyPort, msg, messageCount)

  sys1.scheduler.schedule(30 millisecond, 30 millisecond) {
    if (Counter.count == messageCount) {
      sys1.terminate()
      sys2.terminate()
    }

  }


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


  def init() = {
    implicit val materializer = ActorMaterializer()
    val handler: Sink[IncomingConnection, Future[Done]] = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println("Client connected from: " + conn.remoteAddress)
      //fan-out
      conn handleWith parallelFlow()


    }

    //    // Simple flow mapping ByteStrings to Strings
    //    val protocol: Flow[ByteString, String, _] =
    //      Flow[ByteString].map(_.utf8String)

    //TODO some framing here, i.e. read line by line
    val binding: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind(serverAddress.getHostName, serverAddress.getPort)


    val materializedServer: Future[ServerBinding] = binding.to(handler).run()

    materializedServer.onComplete {
      case Success(address) =>
        println("Server started, listening on: " + address)
      case Failure(e) =>
        println(s"Server could not bind to $serverAddress: ${e.getMessage}")
        system.terminate()
    }
  }

  def logging: BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] = {
    // function that takes a string, prints it with some fixed prefix in front and returns the string again
    def logger(prefix: String) = (chunk: ByteString) => {
      println(s"$prefix  [${chunk.length}] ${chunk.utf8String.substring(0, 30)}")
      chunk
    }

    val inputLogger = logger("> ")
    val outputLogger = logger("< ")

    // create BidiFlow with a separate logger function for each of both streams
    BidiFlow.fromFunctions(inputLogger, outputLogger)
  }


  private def parallelFlow(): Flow[ByteString, ByteString, _] = {
    println(s"Number of connections $numberOfConnections")

    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val balance = b.add(Balance[ByteString](numberOfConnections))
      val mergeFlow: Merge[ByteString] = Merge[ByteString](numberOfConnections)
      val merge = b.add(mergeFlow)

      val tcpOut: Flow[ByteString, ByteString, Future[OutgoingConnection]] = Tcp().outgoingConnection(endPointAdres)//.join(logging)

      println("Constructing via")
      val y: Flow[ByteString, ByteString, Future[OutgoingConnection]] = tcpOut.via(Framing.delimiter(ByteString("\n"), maximumFrameLength = Int.MaxValue, allowTruncation = true))
        .map(data => {
          Counter.count += 1
          if (Counter.count == Counter.total) {
            val processingTime = System.currentTimeMillis() - Counter.start - 3000
            println(s"\nI Sent them all ${processingTime} millis, ${Counter.total.toDouble / processingTime * 1000} TPS")
          }

          data
        })

      1 to numberOfConnections map { data =>
        balance ~> y ~> merge
      }

      FlowShape(balance.in, merge.out)
    })
  }

}


