package org.ieee_passau.evaluation

import java.util.Date
import java.util.concurrent.TimeUnit

import akka.actor.Props
import org.ieee_passau.controllers.Beans.{RunningJobsQ, StatusM}
import play.api.Play.current

import scala.language.postfixOps

// Needed for application context, do not remove!

import org.ieee_passau.evaluation.Messages._
import play.api.libs.concurrent.Akka

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object InputRegulator {
  def props(jobLimit: Int, jobLifetime: Duration): Props = Props(new InputRegulator(jobLimit, jobLifetime))
}

/**
  * Fetches jobs from DBReader.
  * Controls the number of jobs entering the system.
  * Forwards jobs to the job router.
  */
class InputRegulator(jobLimit: Int, jobLifetime: Duration) extends EvaluationActor {
  val STARTUP_DELAY: FiniteDuration = 500 millis
  val TICK_INTERVAL: FiniteDuration = 1 second
  val TICK_MSG = "tick"

  var running = true

  private val fetchedJobs = mutable.HashMap.empty[Job, Date]
  private val tickSchedule = Akka.system.scheduler.schedule(STARTUP_DELAY, TICK_INTERVAL, self, TICK_MSG)

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
          context.actorSelection("../VMMaster") ! JobM(job)
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
          context.actorSelection("../DBReader") ! ReadJobsDB(jobLimit)
        }
      }

    case JobFinished(job) =>
      fetchedJobs.remove(job)

    case JobFailure(job) =>
      log.debug("InputRegulator was notified about job failure of %s".format(job))
      fetchedJobs.remove(job)
  }
}
