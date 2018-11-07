package org.ieee_passau.evaluation

import java.util.UUID

import akka.actor.ActorSystem
import com.google.inject.Inject
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.StringHelper._
import org.ieee_passau.utils.{FutureHelper, MathHelper}
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, ExecutionContext}

object DBReader {
  trait Factory {
    def apply(): DBReader
  }
}

/**
  * If prompted by InputRegulator, reads jobs from the database and sends them to InputRegulator.
  */
class DBReader @Inject() (val dbConfigProvider: DatabaseConfigProvider,
                          val config: Configuration,
                          val system: ActorSystem) extends EvaluationActor {
  private implicit val db: Database = dbConfigProvider.get[JdbcProfile].db
  private implicit val evalContext: ExecutionContext = system.dispatchers.lookup("evaluator.context")

  override def receive: Receive = {
    case ReadJobsDB(count) =>
      log.debug("DBReader received request for %d jobs".format(count))

      val query = for {
        tr <- Testruns if tr.stage.?.isDefined // || tr.result === (Queued: Result)
        tc <- Testcases if tc.id === tr.testcaseId
        sl <- Solutions if sl.id === tr.solutionId
        pr <- Problems if pr.id === sl.problemId
      } yield (pr.id, pr.cpuFactor, pr.memFactor, sl.program, sl.programName, sl.language, tc.input, tc.expectedOutput, tr.progOut.?, tr.id, tr.stage)

      db.run(query.sortBy(_._1.asc).take(count).result).foreach { rawJobs =>
        val jobs = rawJobs.map { rawJob =>
          val uuid = UUID.randomUUID().toString
          if (rawJob._11 /*stage*/ == 0) { // normal evaluation job
            Messages.BaseJob(
              cpuFactor = MathHelper.makeDuration(config.getOptional[String]("evaluator.eval.basetime").getOrElse("60 seconds")).mul(rawJob._2).toSeconds,
              memFactor = (rawJob._3 * config.getOptional[Int]("evaluator.eval.basemem").getOrElse(100)).floor.toInt,
              lang = rawJob._6,
              testrunId = rawJob._10,
              evalId = uuid,
              program = rawJob._4,
              programName = rawJob._5,
              stdin = cleanNewlines(rawJob._7),
              expectedOut = cleanNewlines(rawJob._8)
            )
          } else { // never the less, create a new task, but now for higher stage
            val taskQuery = EvalTasks.filter(_.position === rawJob._11).filter(_.problemId === rawJob._1)
            Await.result(db.run(taskQuery.result.head) map { task =>
              Messages.NextStageJob(
                testrunId = rawJob._10,
                stage = rawJob._11,
                evalId = uuid,
                program = rawJob._4,
                stdin = cleanNewlines(rawJob._7),
                expectedOut = cleanNewlines(rawJob._8),
                progOut = cleanNewlines(rawJob._9.getOrElse("")),
                command = task.command,
                inputData = (task.useStdin, task.useProgout, task.useExpout, task.useProgram),
                outputStdoutCheck = task.outputCheck,
                outputScore = task.scoreCalc,
                programName = task.filename,
                file = task.file
              )
            }, FutureHelper.dbTimeout)
          }
        }

        log.debug("DBReader is sending %d jobs to InputRegulator".format(jobs.length))
        sender ! JobsM(jobs.toList)
      }
  }
}
