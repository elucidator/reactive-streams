package com.xebia.util

import java.net.InetSocketAddress
import akka.actor._
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.io._
import akka.util.ByteString

object AsyncLatencyEchoServer extends App {

  val system = ActorSystem("echo-service-system")
  val endpoint = new InetSocketAddress("localhost", 11111)
  val delay = Option(1000)
  println(s"============ Delay is: ${delay.map(_.toString).getOrElse("No Delay")}")
  system.actorOf(EchoService.props(endpoint, delay), "echo-service")

  io.StdIn.readLine(s"Hit ENTER to exit ...${System.getProperty("line.separator")}")
  system.shutdown()
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

  // We need to know when the connection dies without sending a `Tcp.ConnectionClosed`
  context.watch(connection)

  override def receive: Receive = doReceive(replyHandler)

  private def doReceive(replyHandler: (String, ActorRef, ActorRef) => Unit): Receive = {
    case Tcp.Received(data) =>
      val text = data.utf8String.trim
      //println(s"Received '$text' from remote address $remote")
      text match {
        case "close" => context.stop(self)
        case "reset" =>
          counter = 0
          sender ! Tcp.Write(ByteString(s"$counter"))
        case _ =>
          counter += 1
          replyHandler(s"$counter: $text\n", sender(), connection)
      }
    case _: Tcp.ConnectionClosed =>
      println(s"Stopping, because connection for remote address $remote closed")
      context.stop(self)
    case Terminated(`connection`) =>
      println(s"Stopping, because connection for remote address $remote died")
      context.stop(self)
  }

  //delays when delay is set
  private def replyHandler(text: String, actorRef: ActorRef, connection:ActorRef): Unit = delayCfg.map { delay =>
    connection ! Tcp.SuspendReading
    context.system.scheduler.scheduleOnce(delay milliseconds){
      actorRef ! Tcp.Write(ByteString(text))
      connection ! Tcp.ResumeReading
    }
  } getOrElse {
    actorRef ! Tcp.Write(ByteString(text))
  }

}

