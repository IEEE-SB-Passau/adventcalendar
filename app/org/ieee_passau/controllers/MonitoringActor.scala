package org.ieee_passau.controllers

import java.util.Date

import akka.actor.Actor
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.ieee_passau.controllers.Beans._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps


// TODO i18n
class MonitoringActor extends Actor {
  private var running = true
  private var status = "<a href=\"https://ieee.uni-passau.de/de/veranstaltungen/wettbewerbe/adventskalender-status-changelog/\">Schau mal hier vorbei</a>"
  implicit val timeout = Timeout(5000 milliseconds)

  private val nodes = mutable.HashMap[String, VMStatus]()

  override def receive: Receive = {
    case StatusQ => sender ! StatusM(running, status)
    case StatusM(state, message) =>
      running = state
      status = message
      context.actorSelection("../Evaluator/VMMaster") ! StatusM(state, message)
      context.actorSelection("../Evaluator/InputRegulator") ! StatusM(state, message)
    case RunningJobsQ => pipe (context.actorSelection("../Evaluator/InputRegulator") ? RunningJobsQ) to sender
    case RunningVMsQ => pipe ((context.actorSelection("../Evaluator/VMMaster") ? RunningVMsQ) flatMap {
      case list: List[(String, Int) @unchecked] => Future {
        list.map {
          vm: (String, Int) => (vm._1, vm._2, nodes.getOrElse(vm._1, VMStatus(vm._1, 0, 0, 0, 0, new Date)))
        }
      }
    }) to sender
    case VMStatusM(state) => nodes += ((state.actorName, state))
  }
}
