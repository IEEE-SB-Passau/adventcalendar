package org.ieee_passau.evaluation

import java.util.Date

import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.StringHelper
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._

/**
  * Writes evaluated jobs to the database and broadcasts a JobFinished message.
  */
class DBWriter extends EvaluationActor {

  override def receive: Receive = {
    case EvaluatedJobM(eJob) =>
      DB.withSession { implicit session =>
        log.debug("DBWriter is saving %s to database".format(eJob.job))

        def stripNull = (x: Option[String]) => if (x.isDefined) Some(StringHelper.stripChars(x.get, "\u0000")) else None

        // Update testrun in database with evaluation results
        val maybeTR = Testruns.byId(eJob.job.testrunId).firstOption
        if (maybeTR.isDefined && maybeTR.get.stage.isDefined) {
          val tr = maybeTR.get
          val nextStage = (for {
              s <- Solutions if s.id === tr.solutionId
              p <- s.problem
              t <- EvalTasks if t.problemId === p.id && t.position > tr.stage && ((t.runCorrect && eJob.result.contains(Passed)) ||
                                                                                  (t.runWrong && eJob.result.contains(WrongAnswer)) ||
                                                                                   eJob.result.isEmpty)
            } yield t.position).sortBy(_.asc).firstOption

          val oldTr = Testruns.byId(eJob.job.testrunId).first

          Testruns.update(eJob.job.testrunId, oldTr.copy(
            progOut     = if (eJob.progOut.isDefined)  stripNull(eJob.progOut) else oldTr.progOut,
            progErr     = if (eJob.progErr.isDefined)  stripNull(eJob.progErr) else oldTr.progErr,
            progExit    = if (eJob.progExit.isDefined) eJob.progExit           else oldTr.progExit,
            progRuntime = Some(oldTr.progRuntime.getOrElse(0.0) + eJob.progRuntime.getOrElse(0.0)),
            progMemory  = Some(math.max(oldTr.progMemory.getOrElse(0), eJob.progMemory.getOrElse(0))),

            compOut     = if (eJob.compOut.isDefined)  stripNull(eJob.compOut) else oldTr.compOut,
            compErr     = if (eJob.compErr.isDefined)  stripNull(eJob.compErr) else oldTr.compErr,
            compExit    = if (eJob.compExit.isDefined) eJob.compExit           else oldTr.compExit,
            compRuntime = Some(oldTr.compRuntime.getOrElse(0.0) + eJob.compRuntime.getOrElse(0.0)),
            compMemory  = Some(math.max(oldTr.compMemory.getOrElse(0), eJob.compMemory.getOrElse(0))),

            score       = if (eJob.score.isDefined)    eJob.score              else oldTr.score,
            result      = if (eJob.result.isDefined)   eJob.result.get         else oldTr.result,

            stage     = nextStage,
            completed = new Date(),

            vm = Some((oldTr.vm match { case Some(vms) => vms + " " case None => ""}) + sender.path.name)
          ))
        }

        // Broadcast JobFinished
        log.info("DBWriter is broadcasting JobFinished for %s".format(eJob.job))
        context.system.eventStream.publish(JobFinished(eJob.job))
      }
  }
}
