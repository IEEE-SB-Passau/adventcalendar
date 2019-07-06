package org.ieee_passau.evaluation

import java.util.Date

import akka.actor.ActorSystem
import com.google.inject.Inject
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.DbHelper.retry
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
  private implicit val logger: Logger = Logger(this.getClass)

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
              // only update results if last stage
              retry(for {
                oldTr <- Testruns.byId(eJob.job.testrunId).result.head
                testrun <- DBIO.successful(copyTrFromOld(oldTr, None))
                _ <- Testruns.update(oldTr.id.get, testrun.withId(oldTr.id.get))
              } yield ())
            case Some(stage) =>
              // update only output and advance stage
              retry(for {
                oldTr <- Testruns.byId(eJob.job.testrunId).result.head
                testrun <- DBIO.successful(copyTrFromOld(oldTr, Some(stage)))
                _ <- Testruns.byId(oldTr.id.get).update(testrun.withId(oldTr.id.get))
              } yield ())
          }
        case _ =>

      } foreach { _ =>
        log.info("DBWriter is broadcasting JobFinished for %s".format(eJob.job))
        context.system.eventStream.publish(JobFinished(eJob.job))
      }
  }
}
