package org.ieee_passau.controllers

import java.util.Date

import akka.actor.Actor
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.evaluation.Messages.JobFinished

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class MonitoringActor extends Actor {
  private var running = true
  implicit val timeout = Timeout(5000 milliseconds)

  private val nodes = mutable.HashMap[String, VMStatus]()

  override def receive: Receive = {
    case StatusQ => sender ! StatusM(running)
    case StatusM(state) =>
      running = state
      context.actorSelection("../Evaluator/VMMaster") ! StatusM(state)
      context.actorSelection("../Evaluator/InputRegulator") ! StatusM(state)
    case RunningJobsQ => pipe (context.actorSelection("../Evaluator/InputRegulator") ? RunningJobsQ) to sender
    case RunningVMsQ => pipe ((context.actorSelection("../Evaluator/VMMaster") ? RunningVMsQ) flatMap {
      case list: List[(String, Int) @unchecked] => Future {
        list.map {
          vm: (String, Int) => (vm._1, vm._2, nodes.getOrElse(vm._1, VMStatus(vm._1, "", 0, 0, 0, 0, new Date)))
        }
      }
    }) to sender
    case VMStatusM(state) => nodes += ((state.actorName, state))
    case JobFinished(job) => context.actorSelection("../Evaluator/InputRegulator") ! JobFinished(job)
  }
}
