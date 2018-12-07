package org.ieee_passau.evaluation

import java.util.Date

import akka.actor.ActorSystem
import com.google.inject.Inject
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.DbHelper.retry
import org.ieee_passau.utils.ListHelper.reduceResult
import org.ieee_passau.utils.StringHelper.stripNull
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

object DBWriter {
  trait Factory {
    def apply(): DBWriter
  }
}

/**
  * Writes evaluated jobs to the database and broadcasts a JobFinished message.
  */
class DBWriter @Inject()(val dbConfigProvider: DatabaseConfigProvider, val system: ActorSystem) extends EvaluationActor {
  private implicit val db: Database = dbConfigProvider.get[JdbcProfile].db
  private implicit val evalContext: ExecutionContext = system.dispatchers.lookup("evaluator.context")
  private implicit val logger = Logger(this.getClass)

  override def receive: Receive = {
    case EvaluatedJobM(eJob) =>
      val source = sender

      def copyTrFromOld(oldTr: Testrun, nextStage: Option[Int]) = {
        oldTr.copy(
          progOut     = if (eJob.progOut.isDefined) stripNull(eJob.progOut) else oldTr.progOut,
          progErr     = if (eJob.progErr.isDefined) stripNull(eJob.progErr) else oldTr.progErr,
          progExit    = if (eJob.progExit.isDefined) eJob.progExit else oldTr.progExit,
          progRuntime = Some(oldTr.progRuntime.getOrElse(0.0) + eJob.progRuntime.getOrElse(0.0)),
          progMemory  = Some(math.max(oldTr.progMemory.getOrElse(0), eJob.progMemory.getOrElse(0))),

          compOut     = if (eJob.compOut.isDefined) stripNull(eJob.compOut) else oldTr.compOut,
          compErr     = if (eJob.compErr.isDefined) stripNull(eJob.compErr) else oldTr.compErr,
          compExit    = if (eJob.compExit.isDefined) eJob.compExit else oldTr.compExit,
          compRuntime = Some(oldTr.compRuntime.getOrElse(0.0) + eJob.compRuntime.getOrElse(0.0)),
          compMemory  = Some(math.max(oldTr.compMemory.getOrElse(0), eJob.compMemory.getOrElse(0))),

          score       = if (eJob.score.isDefined) eJob.score else oldTr.score,
          result      = if (eJob.result.isDefined) eJob.result.get else oldTr.result,

          stage       = nextStage,
          completed   = new Date(),

          evalId      = Some(eJob.job.evalId),
          vm          = Some(
            (oldTr.vm match {
              case Some(vms) => vms + " "
              case None      => ""
            }) + source.path.name)
        )
      }

      def getNewScore(current: Int, points: Int) = {
        eJob.job match {
          // we need to consider the scoring factor determined by the backend
          case NextStageJob(_, _, _, _, _, _, _, _, _, _, true, _, _) =>
            // points * (1 / score) for score >= 1, => lower score results in higher points
            current + (points.toDouble / (if (eJob.score.isEmpty || eJob.score.get == 0) 1d else eJob.score.get.toDouble)).floor.toInt
          // no need to consider score, because it's only one stage, or the last stage does not score so we just add the points directly
          case _ => current + points
        }
      }

      def otherResults(tr: Testrun) =
        Testruns.filter(r => r.solutionId === tr.solutionId && r.id =!= eJob.job.testrunId).map(r => (r.result, r.stage)).result

      def newResult(other: Seq[(Result, Option[Int])]) =
        reduceResult(other :+ (eJob.result.getOrElse(Canceled), None))

      /**
        * @return `None` if there is no stage left or else the stage number to queue next
        */
      def nextStageQ(tr: Testrun) = (for {
        s <- Solutions if s.id === tr.solutionId
        p <- s.problem
        t <- EvalTasks if t.problemId === p.id && t.position > tr.stage && (
          (t.runCorrect && eJob.result.contains(Passed)     ) ||
          (t.runWrong   && eJob.result.contains(WrongAnswer)) ||
                           eJob.result.isEmpty)
      } yield t.position).sortBy(_.asc).result.headOption

      log.debug("DBWriter is saving %s to database".format(eJob.job))

      // Update testrun in database with evaluation results
      db.run(Testruns.byId(eJob.job.testrunId).extract.filter(_.stage.isDefined).result.headOption).map {
        case Some(tr) =>
          db.run(nextStageQ(tr)).foreach {
            case None =>
              if (eJob.result.fold(false)(_ == Passed)) {
                retry(for {
                  results <- otherResults(tr)
                  points <- Testcases.filter(_.id === tr.testcaseId).map(_.points).result.head
                  score <- Solutions.filter(_.id === tr.solutionId).map(_.score).result.head

                  result <- DBIO.successful(newResult(results))
                  newScore <- DBIO.successful(getNewScore(score, points))

                  oldTr <- Testruns.byId(eJob.job.testrunId).result.head
                  testrun <- DBIO.successful(copyTrFromOld(oldTr, None))

                  _ <- Solutions.filter(_.id === tr.solutionId).map(sol => (sol.score, sol.result)).update((newScore, result))
                  _ <- Testruns.update(oldTr.id.get, testrun.withId(oldTr.id.get))
                } yield ())
              } else {
                retry(for {
                  results <- otherResults(tr)
                  result <- DBIO.successful(newResult(results))

                  oldTr <- Testruns.byId(eJob.job.testrunId).result.head
                  testrun <- DBIO.successful(copyTrFromOld(oldTr, None))

                  _ <- Solutions.filter(_.id === tr.solutionId).map(_.result).update(result)
                  _ <- Testruns.byId(oldTr.id.get).update(testrun.withId(oldTr.id.get))
                } yield ())
              }
            case _ =>
          }
        case _ =>

      } foreach { _ =>
        log.info("DBWriter is broadcasting JobFinished for %s".format(eJob.job))
        context.system.eventStream.publish(JobFinished(eJob.job))
      }
  }
}
