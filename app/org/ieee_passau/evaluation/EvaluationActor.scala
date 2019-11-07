package org.ieee_passau.evaluation

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import org.ieee_passau.evaluation.Messages._

import scala.concurrent.duration._
import scala.language.postfixOps

abstract class EvaluationActor extends Actor {
  val log: LoggingAdapter = Logging(context.system, this)
  val MAX_RETRIES = 10
  val RESTART_WINDOW: FiniteDuration = 60 seconds

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(
      maxNrOfRetries = MAX_RETRIES,
      withinTimeRange = RESTART_WINDOW,
      loggingEnabled = true) {
    case _ => SupervisorStrategy.Restart
  }

  private def subscribe = {
    context.system.eventStream.subscribe(context.self, classOf[JobFailure])
    context.system.eventStream.subscribe(context.self, classOf[JobFinished])
  }

  private def unsubscribe = {
    if (context != null) {
      context.system.eventStream.unsubscribe(context.self, classOf[JobFailure])
      context.system.eventStream.unsubscribe(context.self, classOf[JobFinished])
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    subscribe
  }

  override def postStop(): Unit = {
    super.postStop()
    unsubscribe
  }
}
