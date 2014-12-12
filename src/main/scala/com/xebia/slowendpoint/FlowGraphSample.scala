package com.xebia.slowendpoint

import akka.stream.scaladsl.PublisherSink
import akka.stream.scaladsl.FlowGraphImplicits
import akka.stream.scaladsl.IterableSource
import akka.stream.scaladsl._
import akka.stream.scaladsl.FlowGraph
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.Flow
import akka.stream.FlowMaterializer
import akka.actor.ActorSystem

object FlowGraphSample extends App {
  val bcast = Broadcast[String]
  val merge = Merge[String]
  val in1 = IterableSource(List("a", "b", "c"))
  val out1 = ForeachSink(println)

  val flow: Flow[String, String] = Flow[String].map(_.toUpperCase)

  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  println("====================== flow directly ==============================:");
  flow.runWith(in1, out1)

  println("====================== flowgraph ==============================:");
  val graph = FlowGraph { implicit b ⇒
    import FlowGraphImplicits._
    in1 ~> flow ~> bcast ~> flow ~> merge ~> out1
    //in1 ~> flow ~> bcast
    1 to 10 map { _ =>
      bcast ~> Flow[String].map(_.toUpperCase) ~> merge

    }
  }.run()

  println("====================== partial ==============================:");
  val partialFlow = PartialFlowGraph { implicit b ⇒
    import FlowGraphImplicits._
    UndefinedSource("no1") ~> flow ~> bcast ~> flow ~> merge ~> UndefinedSink("n2")
    //bcast
    1 to 10 map { _ =>
      bcast ~> Flow[String].map(_.toUpperCase) ~> merge
    }
    //merge
  }.toFlow(UndefinedSource("no1"), UndefinedSink("n2"))

  partialFlow.runWith(in1, out1)
  actorSystem.shutdown

}


 