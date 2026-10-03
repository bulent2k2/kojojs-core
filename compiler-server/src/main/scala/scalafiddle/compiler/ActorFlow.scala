/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package scalafiddle.compiler

import akka.actor._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}

/**
  * Provides a flow that is handled by an actor.
  *
  * Copied from Play
  *
  * See https://github.com/akka/akka/issues/16985.
  */
object ActorFlow {

  /**
    * Create a flow that is handled by an actor.
    *
    * Messages can be sent downstream by sending them to the actor passed into the props function.  This actor meets
    * the contract of the actor returned by [[akka.stream.scaladsl.Source.actorRef]].
    *
    * The props function should return the props for an actor to handle the flow. This actor will be created using the
    * passed in [[akka.actor.ActorRefFactory]]. Each message received will be sent to the actor - there is no back pressure,
    * if the actor is unable to process the messages, they will queue up in the actors mailbox. The upstream can be
    * cancelled by the actor terminating itself.
    *
    * @param props A function that creates the props for actor to handle the flow.
    * @param bufferSize The maximum number of elements to buffer.
    * @param overflowStrategy The strategy for how to handle a buffer overflow.
    */
  def actorRef[In, Out](props: ActorRef => Props,
                        bufferSize: Int = 16,
                        overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew)(
      implicit factory: ActorRefFactory,
      mat: Materializer): Flow[In, Out, _] = {

    val (outActor, publisher) = Source
      .actorRef[Out](bufferSize, overflowStrategy)
      .toMat(Sink.asPublisher(false))(Keep.both)
      .run()

    Flow.fromSinkAndSource(
      Sink.actorRef(
        factory.actorOf(Props(new Actor {
          val flowActor = context.watch(context.actorOf(props(outActor), "flowActor"))

          def receive = {
            case Status.Success(_) | Status.Failure(_) =>
              flowActor ! PoisonPill
            // `Terminated(_)`, `Terminated` DEĞİL: çıplak ad eşlik nesnesiyle
            // eşitlik sınar ve gerçek Terminated(ref) mesajını hiç yakalamaz;
            // sarmalayıcı çocuğu ölünce durmuyordu (kojojs-core#54). Router'ın
            // kopyasında doğrusu vardı.
            case Terminated(_) =>
              context.stop(self)
            case other => flowActor ! other
          }

          override def supervisorStrategy = OneForOneStrategy() {
            case _ =>
              SupervisorStrategy.Stop
          }

          override def postStop(): Unit = {
            super.postStop()
          }
        })),
        Status.Success(())
      ),
      Source.fromPublisher(publisher)
    )
  }
}
