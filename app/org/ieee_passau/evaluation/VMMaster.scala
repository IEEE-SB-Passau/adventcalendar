package org.ieee_passau.evaluation

import akka.AkkaScopingHelper
import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.routing.{ActorRefRoutee, Router, SmallestMailboxRoutingLogic}
import org.ieee_passau.controllers.Beans.{RunningVMsQ, StatusM}
import org.ieee_passau.evaluation.Messages._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps

object VMMaster {
  trait Factory {
    def apply(): VMMaster
  }
}

/**
  * Manages VMClients. Received jobs are relayed to a VMClient via a router.
  */
class VMMaster extends EvaluationActor with AkkaScopingHelper {

  override val MAX_RETRIES: Int = 2
  override val RESTART_WINDOW: FiniteDuration = 1 minute

  private var router: Router = createRouter()
  private val children = mutable.HashMap[String, ActorRef]()

  private def createRouter(): Router = {
    Router(SmallestMailboxRoutingLogic(), Vector())
  }

  private def addChild(conf: Config): ActorRef = {
    val actor = context.actorOf(
      VMClient.props(conf.host, conf.port, conf.actorName),
      name = conf.actorName
    )

    // register the child
    context.watch(actor)
    router = router.addRoutee(actor)
    children += conf.actorName -> actor

    actor
  }

  override def receive: Receive = {
    case StatusM(false) => router.routees.foreach {
      routee => context.stop(routee.asInstanceOf[ActorRefRoutee].ref)
    }

    case RunningVMsQ =>
      val list = router.routees.map { routee =>
        val temp = routee.asInstanceOf[ActorRefRoutee].ref
        (temp.path.name, getCell(temp).numberOfMessages)
      }.toList
      sender ! list

    case NewVM(config) =>
      val isNew = !children.contains(config.actorName)
      if (isNew) {
        val child = addChild(config)
        log.info("VMMaster added new VMClient %s".format(child.toString()))
      }
      sender ! isNew

    case RemoveVM(name) =>
      val child = children.get(name)
      if (child.isDefined) {
        context.stop(child.get)
        router = router.removeRoutee(child.get)
      }

    case JobM(job) =>
      log.debug("VMMaster received job %d".format(job.testrunId))
      // Relay message to router
      router.route(JobM(job), self)

    case Terminated(actor) =>
      // remove failed routee
      router = router.removeRoutee(actor)
      children -= actor.path.name
      log.info("VMMaster removed VMClient %s".format(actor.toString()))
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }
}
