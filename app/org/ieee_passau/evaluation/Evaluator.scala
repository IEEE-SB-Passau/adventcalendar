package org.ieee_passau.evaluation

import akka.actor.{ActorSystem, Props}
import com.google.inject.Inject
import org.ieee_passau.utils.MathHelper
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider

object Evaluator {
  def props(dbConfigProvider: DatabaseConfigProvider, config: Configuration, system: ActorSystem): Props = Props(new Evaluator(config, dbConfigProvider, system))
}

class Evaluator @Inject() (configuration: Configuration, dbConfigProvider: DatabaseConfigProvider, system: ActorSystem) extends EvaluationActor {

  override def receive: Receive = {
    case _ => // Ignored
  }

  private def start = {
    val jobLimit = configuration.getInt("evaluator.inputregulator.joblimit").getOrElse(1)
    val jobLifetime = MathHelper.makeDuration(configuration.getString("evaluator.eval.basetime").getOrElse("60 seconds")).mul(10)

    // TODO use dependency injection on child actors
    context.actorOf(DBReader.props(dbConfigProvider, configuration), classOf[DBReader].getSimpleName)
    context.actorOf(DBWriter.props(dbConfigProvider), classOf[DBWriter].getSimpleName)
    context.actorOf(Props[VMMaster], classOf[VMMaster].getSimpleName)
    context.actorOf(InputRegulator.props(jobLimit, jobLifetime), classOf[InputRegulator].getSimpleName)
  }

  override def preStart(): Unit = {
    super.preStart()
    this.start
  }
}