package com.xebia.slowendpoint

import akka.NotUsed
import akka.stream.scaladsl._
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, ClosedShape, ActorMaterializer, Materializer}
import akka.actor.ActorSystem

object FlowGraphSample extends App {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val bcast = Broadcast[String](10)
  val merge = Merge[String](10)
  val in1 = Source(List("a", "b", "c"))
  val out1 = Sink.foreach(println)

  val flow = Flow[String].map(_.toUpperCase)

  println("====================== flow directly ==============================:")
  flow.runWith(in1, out1)

  println("====================== flowgraph ==============================:")
  val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val broadcast = b.add(bcast)
    val merge2 = b.add(merge)

    in1 ~> flow ~> broadcast ~> flow ~> merge2 ~> out1

    1 to 10 map { _ ⇒
      broadcast ~> Flow[String].map(_.toUpperCase) ~> merge2
    }

    ClosedShape
  }).run()

  println("====================== partial ==============================:")
  val partialFlow = Flow.fromGraph(GraphDSL.create() { implicit b ⇒
    import GraphDSL.Implicits._

    val broadcast = b.add(bcast)
    val merge2 = b.add(merge)

    // RDJ No idea why this is here?
//    flow ~> broadcast ~> flow ~> merge2

    1 to 10 map { _ ⇒
      broadcast ~> Flow[String].map(_.toUpperCase) ~> merge2
    }

    FlowShape(broadcast.in, merge2.out)
  })

  partialFlow.runWith(in1, out1)

  actorSystem.shutdown

}

