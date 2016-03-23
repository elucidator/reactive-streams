package com.xebia.flowgraph

import akka.actor.Props
import akka.actor.Terminated
import java.net.InetSocketAddress
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Actor
import akka.util.ByteString
import akka.io.IO
import akka.actor.ActorRef
import akka.io.Tcp

object LatencyEndpointServer {

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
      case Tcp.Connected(remote, _) ⇒
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

    private def doReceive(replyHandler: (String, ActorRef, ActorRef) ⇒ Unit): Receive = {
      case Tcp.Received(data) ⇒
        val text = data.utf8String.trim
        text match {
          case "reset" ⇒
            sender ! Tcp.Write(ByteString(s"${counter = 0; counter}"))
          case "close" ⇒ context.stop(self)
          case _ ⇒
            replyHandler(s"${counter += 1; counter}: $text\n", sender(), connection)
        }
      case _: Tcp.ConnectionClosed ⇒
        println(s"Stopping, because connection for remote address $remote closed")
        context.stop(self)
      case Terminated(`connection`) ⇒
        println(s"Stopping, because connection for remote address $remote died")
        context.stop(self)
    }

    //delays when delay is set
    private def replyHandler(text: String, actorRef: ActorRef, connection: ActorRef): Unit = delayCfg.map { delay ⇒
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
