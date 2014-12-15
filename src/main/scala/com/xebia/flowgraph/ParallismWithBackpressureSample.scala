package com.xebia.flowgraph

import java.io.PrintWriter
import java.net.InetSocketAddress
import java.io.OutputStreamWriter
import java.net.Socket

import java.net.InetSocketAddress
import akka.actor._
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.io._
import akka.util.ByteString
import java.net.InetSocketAddress
import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.io.StreamTcp
import akka.stream.scaladsl._
import akka.stream.scaladsl.FlowGraphImplicits._
import akka.util.ByteString
import scala.util.{ Failure, Success }
import akka.stream.OverflowStrategy

object ParallismWithBackpressureSample extends App {
  val msg = s"Some l${"o" * 400}g message"
  val proxyHost = "localhost"
  val proxyPort = 6000
  val endpointHost = "localhost"
  val endpointPort = 11111
  val messageCount = 50000
  val delay = Option(1000)
  val numberOfConnections = 2

  val sys1 = SlowEndpointServer.init(endpointHost, endpointPort, delay)
  val sys2 = ParallelProxyServer.init(proxyHost, proxyPort, endpointHost, endpointPort, numberOfConnections)
  BlockingSocketClient.run(proxyHost, proxyPort, msg, messageCount)

  io.StdIn.readLine(s"Hit ENTER to exit ...${System.getProperty("line.separator")}")
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

  private def getFlow(): Flow[ByteString, ByteString] = {
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

//====================================== CLIENT ============================================//

object BlockingSocketClient {

  def run(host: String, port: Int, msg:String, msgCount: Int) = {
    Thread.sleep(3000)
    new BlockingSocketClient(new InetSocketAddress(host, port), msgCount).sendAndForgetBlocking(msg)
  }
}

class BlockingSocketClient(val serverAddress: InetSocketAddress, msgCount: Int) {

  val serverSocket = {
    val socket = new Socket()
    socket.setSoTimeout(1000)
    socket.connect(serverAddress)
    socket
  }

  def sendAndForgetBlocking(msg: String) = {
    var counter = 0
    val snapshotInterval = 1000
    println(s"Sending $msgCount messages:")
    println(s"${"=" * (msgCount / snapshotInterval)}")
    val (elapsed, _) = measure {
      1 to msgCount foreach { i =>
        writeBlockingMsg(s"$i$msg")
        counter += 1
        if (counter % snapshotInterval == 0) print(".")
      }
    }
    println(s"\n=> Total sent: $msgCount, elapsed $elapsed ms, tps ${msgCount.toDouble / elapsed * 1000}")
  }

  def close() = serverSocket.close

  private def writeBlockingMsg(msg: String): Unit = {
    val out = new PrintWriter(new OutputStreamWriter(serverSocket.getOutputStream(), "utf-8"), true);
    out.println(msg)
    out.flush()
  }

  private def measure[T](callback: ⇒ T): (Long, T) = {
    val start = System.currentTimeMillis
    val res = callback
    val elapsed = System.currentTimeMillis - start
    (elapsed, res)
  }

}
//====================================== SLOW ENDPOINT ============================================//
object SlowEndpointServer {

  def init(host: String = "localhost", port: Int = 11111, delay: Option[Int]): ActorSystem = {
    println(s"============ Delay is: ${delay.map(_.toString).getOrElse("No Delay")}")

    val system = ActorSystem("echo-service-system")
    val listenTo = new InetSocketAddress(host, port)
    system.actorOf(EchoService.props(listenTo, delay), "echo-service")
    system

  }

  object EchoService {
    def props(endpoint: InetSocketAddress, delay: Option[Int]): Props =
      Props(new EchoService(endpoint, delay))
  }

  class EchoService(endpoint: InetSocketAddress, delay: Option[Int]) extends Actor with ActorLogging {
    import context.system
    IO(Tcp) ! Tcp.Bind(self, endpoint)
    override def receive: Receive = {
      case Tcp.Connected(remote, _) =>
        println(s"Remote address $remote connected")
        sender ! Tcp.Register(context.actorOf(EchoConnectionHandler.props(remote, sender, delay)), keepOpenOnPeerClosed = true)
    }

  }
  object EchoConnectionHandler {
    def props(remote: InetSocketAddress, connection: ActorRef, delay: Option[Int]): Props =
      Props(new EchoConnectionHandler(remote, connection, delay))
  }

  class EchoConnectionHandler(remote: InetSocketAddress, connection: ActorRef, delayCfg: Option[Int]) extends Actor with ActorLogging {
    import context.system
    import context.dispatcher
    import scala.concurrent.duration._
    var counter = 0
    context.watch(connection)

    override def receive: Receive = doReceive(replyHandler)

    private def doReceive(replyHandler: (String, ActorRef, ActorRef) => Unit): Receive = {
      case Tcp.Received(data) =>
        val text = data.utf8String.trim
        text match {
          case "reset" =>
            sender ! Tcp.Write(ByteString(s"${counter = 0; counter}"))
          case "close" => context.stop(self)
          case _ =>
            replyHandler(s"${counter += 1; counter}: $text\n", sender(), connection)
        }
      case _: Tcp.ConnectionClosed =>
        println(s"Stopping, because connection for remote address $remote closed")
        context.stop(self)
      case Terminated(`connection`) =>
        println(s"Stopping, because connection for remote address $remote died")
        context.stop(self)
    }

    //delays when delay is set
    private def replyHandler(text: String, actorRef: ActorRef, connection: ActorRef): Unit = delayCfg.map { delay =>
      connection ! Tcp.SuspendReading
      context.system.scheduler.scheduleOnce(delay milliseconds) {
        actorRef ! Tcp.Write(ByteString(text))
        connection ! Tcp.ResumeReading
      }
    } getOrElse {
      actorRef ! Tcp.Write(ByteString(text))
    }

  }
}

