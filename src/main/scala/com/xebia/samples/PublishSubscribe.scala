package com.xebia.samples

import akka.actor.Actor
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage
import akka.stream.actor.WatermarkRequestStrategy
import akka.actor.ActorSystem
import akka.actor.Props
import org.reactivestreams.Subscriber
import akka.stream.scaladsl._
import akka.stream.FlowMaterializer

class IntPublisher extends Actor with ActorPublisher[Int] {
  val random = scala.util.Random
  override def receive: Receive = {
    case ActorPublisherMessage.Request(elements) => {
      println(s"demand is: $elements")
      while (totalDemand > 0) {
        onNext(random.nextInt(80))
      }
    }
  }

}

class PrintSubscriber extends Actor with ActorSubscriber {
  val random = scala.util.Random
  override val requestStrategy = WatermarkRequestStrategy(200)
  override def receive: Receive = {
    case ActorSubscriberMessage.OnNext(msg) => {
      Thread.sleep(200)
      println(s"next is: $msg")
    }
  }

}

object PublishSubscribe extends App {

  // ActorSystem represents the "engine" we run in, including threading configuration and concurrency semantics.
  implicit val system = ActorSystem()

  implicit val materializer = FlowMaterializer()

  val circleProducer = system.actorOf(Props[IntPublisher])
  val displaySubscriber = system.actorOf(Props[PrintSubscriber])

  //    ActorPublisher(circleProducer).subscribe( ActorSubscriber(displaySubscriber))

  val subscriber: Subscriber[Any] = ActorSubscriber(displaySubscriber)
  Source(ActorPublisher(circleProducer)).runWith(Sink(subscriber))

}