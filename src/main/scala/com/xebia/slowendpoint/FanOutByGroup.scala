package com.xebia.slowendpoint

import java.net.InetSocketAddress
import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.Await
import akka.stream.io.StreamTcp
import akka.stream.scaladsl.IterableSource
import akka.stream.scaladsl.ForeachSink
import scala.util.Try
import akka.util.ByteString
object FanOutByGroup {
  val endPointAdres: InetSocketAddress = new InetSocketAddress("127.0.0.1", 11111);

  val to = 5000000
  val messages = List("[DEBUG] debug statement", "[INFO] statement", "[WARN] statement", "[ERROR] statement")
  val LoglevelPattern = """.*\[(DEBUG|INFO|WARN|ERROR)\].*""".r

  def main(args: Array[String]): Unit = {
    // actor system and implicit materializer
    implicit val system = ActorSystem("Sys")
    // execution context
    import system.dispatcher
    implicit val materializer = FlowMaterializer()

    //Note: A single group also does not work as expected. After n-amount of elements (~ 100000) no more elements are sent. A Bug? 
    def sendSingleGroup = {
      val source = IterableSource(1 to to map (i => messages(i % 4)))
        .map(line => ByteString.fromString(line + "\n"))

      StreamTcp().outgoingConnection(endPointAdres).flow
        .runWith(source, new MeasureSink("1", to).measureSink)

    }

    //NOTE: multiple groups do not work as expected either . After about ~100000 messages no more messages are sent. A Bug?
    def multipleGroups = {
      IterableSource(1 to to map (i => messages(i % 4)))
        .groupBy {
          case LoglevelPattern(group) => group
          case other => other
        }
        .foreach {
          case (group, stringSource) =>
            println(s"Group: $group")
            StreamTcp().outgoingConnection(endPointAdres).flow
              .runWith(stringSource.map(line => ByteString.fromString(line + "\n")), new MeasureSink(group, to).measureSink)
        }
    }
    //test with:
    multipleGroups
  }

}

class MeasureSink(val group: Any, val to: Int) {
  val start = System.currentTimeMillis()
  var counter = 0
  val measureSink = ForeachSink((i: ByteString) => {
    counter += 1
    if (counter % 100 == 0) printTPS
    if (counter >= to) printTPS
  })
  def printTPS = {
    val elapsed = System.currentTimeMillis() - start
    println(s"${Thread.currentThread.getName} group: $group ===> Total sent: $counter, elapsed $elapsed ms, tps ${counter.toDouble / elapsed * 1000}")

  }

}



