package org.ieee_passau.evaluation

import akka.actor.Props
import org.ieee_passau.utils.MathHelper
import play.api.Play.current
import play.api.libs.concurrent.Akka

object Evaluator {
  def start = {
    if (play.Configuration.root().getBoolean("evaluator.run", false)) {
      Akka.system.actorOf(Props[Evaluator], name = "Evaluator")
    }
  }
}

class Evaluator extends EvaluationActor {
  override def receive: Receive = {
    case _ => // Ignored
  }

  private def start = {
    val cfg = play.Configuration.root()
    val jobLimit = cfg.getInt("evaluator.inputregulator.joblimit", 1)
    val jobLifetime = MathHelper.makeDuration(cfg.getString("evaluator.inputregulator.joblifetime", "10 minutes"))

    context.actorOf(Props[DBReader], name = "DBReader")
    context.actorOf(Props[DBWriter], name = "DBWriter")
    context.actorOf(Props[VMMaster], name = "VMMaster")
    context.actorOf(InputRegulator.props(jobLimit, jobLifetime), name = "InputRegulator")
  }

  override def preStart(): Unit = {
    super.preStart()
    this.start
  }
}
