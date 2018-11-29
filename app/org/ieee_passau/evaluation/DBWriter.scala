package org.ieee_passau.evaluation

import java.util.Date

import akka.actor.ActorSystem
import com.google.inject.Inject
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.ListHelper.reduceResult
import org.ieee_passau.utils.StringHelper.stripNull
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext
import scala.util.Try

object DBWriter {
  trait Factory {
    def apply(): DBWriter
  }
}

/**
  * Writes evaluated jobs to the database and broadcasts a JobFinished message.
  */
class DBWriter @Inject() (val dbConfigProvider: DatabaseConfigProvider, val system: ActorSystem) extends EvaluationActor {
  private implicit val db: Database = dbConfigProvider.get[JdbcProfile].db
  private implicit val evalContext: ExecutionContext = system.dispatchers.lookup("evaluator.context")

  override def receive: Receive = {
    case EvaluatedJobM(eJob) =>
      val source = sender

      log.debug("DBWriter is saving %s to database".format(eJob.job))

      // Update testrun in database with evaluation results
      db.run(Testruns.byId(eJob.job.testrunId).result.headOption).foreach { maybeTR =>
        if (maybeTR.isDefined && maybeTR.get.stage.isDefined) {
          val tr = maybeTR.get

          // returns `None` if there is no stage left or else the stage number to queue next
          val nextStageQuery = (for {
            s <- Solutions if s.id === tr.solutionId
            p <- s.problem
            t <- EvalTasks if t.problemId === p.id && t.position > tr.stage && (
                              (t.runCorrect && eJob.result.contains(Passed)     ) ||
                              (t.runWrong   && eJob.result.contains(WrongAnswer)) ||
                                               eJob.result.isEmpty)
          } yield t.position).sortBy(_.asc).result.headOption

          db.run(nextStageQuery).foreach { nextStage =>
            db.run(Solutions.filter(_.id === tr.solutionId).result.head).map { solution =>
              db.run(Testruns.filter(r => r.solutionId === solution.id.get && r.id =!= eJob.job.testrunId).map(r => (r.result, r.stage)).result).map { results =>
                val result = reduceResult(results :+ (eJob.result.getOrElse(Canceled), nextStage))

                if (eJob.result.fold(false)(_ == Passed) && nextStage.isEmpty) { // only last updates score, and only if it passed
                  db.run(Testcases.filter(_.id === tr.testcaseId).map(_.points).result.head).map { points =>
                    val newPoints = eJob.job match {

                      // we need to consider the scoring factor determined by the backend
                      case NextStageJob(_, _, _, _, _, _, _, _, _, _, true, _, _) =>
                        // points * (1 / score) for score >= 1, => lower score results in higher points
                        (points * Try(1d / eJob.score.getOrElse(1)).getOrElse(1d)).floor.toInt

                      // no need to consider score, because it's only one stage, or the last stage does not score so we just add the points directly
                      case _ => solution.score + points
                    }
                    Solutions.update(tr.solutionId, solution.copy(score = newPoints, result = result))
                  }
                } else {
                  Solutions.update(tr.solutionId, solution.copy(result = result))
                }
              }
            }

            db.run(Testruns.byId(eJob.job.testrunId).result.head).foreach { oldTr =>
              Testruns.update(oldTr.id.get, oldTr.copy(
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

                stage       = nextStage,
                completed   = new Date(),

                evalId      = Some(eJob.job.evalId),
                vm          = Some((oldTr.vm match { case Some(vms) => vms + " " case None => ""}) + source.path.name)
              ))
            }

            log.info("DBWriter is broadcasting JobFinished for %s".format(eJob.job))
            context.system.eventStream.publish(JobFinished(eJob.job))
          }
        }
      }
  }
}
