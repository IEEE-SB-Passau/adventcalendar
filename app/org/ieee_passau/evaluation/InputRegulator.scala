package org.ieee_passau.evaluation

import java.util.Date
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.ieee_passau.controllers.Beans.{RunningJobsQ, StatusM}
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.utils.{AkkaHelper, MathHelper}
import play.api.Configuration

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object InputRegulator {
  trait Factory {
    def apply(jobLimit: Int, jobLifetime: Duration): InputRegulator
  }
}

/**
  * Fetches jobs from DBReader.
  * Controls the number of jobs entering the system.
  * Forwards jobs to the job router.
  */
class InputRegulator @Inject() (@Assisted val jobLimit: Int,
                                @Assisted val jobLifetime: Duration,
                                val config: Configuration,
                                val system: ActorSystem
                               ) extends EvaluationActor {
  implicit private val evalContext: ExecutionContext = system.dispatchers.lookup("evaluator.context")

  val STARTUP_DELAY: FiniteDuration = MathHelper.makeDuration(config.getOptional[String]("evaluator.inputregulator.startupdelay").getOrElse("2 minutes"))
  val TICK_INTERVAL: FiniteDuration = MathHelper.makeDuration(config.getOptional[String]("evaluator.inputregulator.ticktime").getOrElse("1 second"))
  val TICK_MSG = "tick"

  private var running = config.getOptional[Boolean]("evaluator.run").getOrElse(true)

  private val fetchedJobs = mutable.HashMap.empty[Job, Date]
  private val tickSchedule = system.scheduler.schedule(STARTUP_DELAY, TICK_INTERVAL, self, TICK_MSG)

  override def postStop(): Unit = {
    super.postStop()
    tickSchedule.cancel()
  }

  override def receive: Receive = {
    case StatusM(state) =>
      fetchedJobs.clear()
      running = state

    case RunningJobsQ => sender ! fetchedJobs.toList

    case JobsM(jobs) =>
      // Handle jobs received from DBReader
      log.debug("InputRegulator received %d jobs".format(jobs.length))
      val now = new Date()

      jobs.foreach((job: Job) => {
        if (fetchedJobs.size < jobLimit && !fetchedJobs.contains(job)) {
          fetchedJobs.put(job, now)
          // Forward job to router
          context.actorSelection(AkkaHelper.evalPath + classOf[VMMaster].getSimpleName) ! JobM(job)
          log.info("InputRegulator accepted %s".format(job))
        }
      })

    case TICK_MSG =>
      if (running) {
        // Remove jobs older than jobLifetime
        val now = new Date()
        fetchedJobs.retain((_: Job, accepted: Date) => {
          Duration(now.getTime - accepted.getTime, TimeUnit.MILLISECONDS) <= jobLifetime
        })

        // Request new jobs if job limit is not reached
        if (fetchedJobs.size < jobLimit) {
          // Always request a gratuitous amount of jobs from the DB to avoid receiving only jobs we already know!
          context.actorSelection(AkkaHelper.evalPath + classOf[DBReader].getSimpleName) ! ReadJobsDB(jobLimit)
        }
      }

    case JobFinished(job) =>
      fetchedJobs.remove(job)

    case JobFailure(job) =>
      log.debug("InputRegulator was notified about job failure of %s".format(job))
      fetchedJobs.remove(job)
  }
}
