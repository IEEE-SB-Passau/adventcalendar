package org.ieee_passau.evaluation

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import com.google.inject.Inject
import org.ieee_passau.utils.FutureHelper
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.InjectedActorSupport

class Evaluator @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                          val config: Configuration,
                          val system: ActorSystem,
                          val dbReaderFactory: DBReader.Factory,
                          val dbWriterFactory: DBWriter.Factory,
                          val vmMasterFactory: VMMaster.Factory,
                          val inputRegulatorFactory: InputRegulator.Factory
                         ) extends EvaluationActor with InjectedActorSupport {

  override def receive: Receive = {
    case _ => // Ignored
  }

  private def start = {
    val jobLimit = config.getOptional[Int]("evaluator.inputregulator.joblimit").getOrElse(1)
    val jobLifetime = FutureHelper.makeDuration(config.getOptional[String]("evaluator.eval.basetime").getOrElse("60 seconds")).mul(10)

    injectedChild(dbReaderFactory(), classOf[DBReader].getSimpleName)
    injectedChild(dbWriterFactory(), classOf[DBWriter].getSimpleName)
    injectedChild(vmMasterFactory(), classOf[VMMaster].getSimpleName)
    injectedChild(inputRegulatorFactory(jobLimit, jobLifetime), classOf[InputRegulator].getSimpleName)
  }

  override def preStart(): Unit = {
    super.preStart()
    this.start
  }

  override def injectedChild(create: => Actor, name: String, props: Props => Props = identity)(implicit context: ActorContext): ActorRef = {
    context.actorOf(props(Props(create).withDispatcher("evaluator.context")), name)
  }
}
