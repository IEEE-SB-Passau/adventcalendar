package org.ieee_passau.evaluation

import java.util.UUID

import akka.actor.ActorSystem
import com.google.inject.Inject
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.FutureHelper
import org.ieee_passau.utils.StringHelper._
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
      val source = sender
      log.debug("DBReader received request for %d jobs".format(count))

      val query = for {
        tr <- Testruns if tr.stage.isDefined
        tc <- tr.testcase
        sl <- tr.solution
        pr <- sl.problem
        cl <- sl.language
      } yield (
        /* 1*/ pr.id,
        /* 2*/ pr.cpuFactor,
        /* 3*/ cl.cpuFactor,
        /* 4*/ pr.memFactor,
        /* 5*/ cl.memFactor,
        /* 6*/ sl.languageId,
        /* 7*/ sl.program,
        /* 8*/ sl.programName,
        /* 9*/ tc.input,
        /*10*/ tc.expectedOutput,
        /*11*/ tr.progOut,
        /*12*/ tr.id,
        /*13*/ tr.stage)

      db.run(query.sortBy(_._1.asc).take(count).result).foreach { rawJobs =>
        val jobs = rawJobs.map { rawJob =>
          val uuid = UUID.randomUUID().toString
          if (rawJob._13.get /*stage*/ == 0) { // normal evaluation job
            Messages.BaseJob(
              cpuFactor = FutureHelper.makeDuration(config.getOptional[String]("evaluator.eval.basetime").getOrElse("60 seconds")).mul(rawJob._2).mul(rawJob._3).toSeconds,
              memFactor = (rawJob._4 * rawJob._5 * config.getOptional[Int]("evaluator.eval.basemem").getOrElse(100)).floor.toInt,
              lang = rawJob._6,
              testrunId = rawJob._12,
              evalId = uuid,
              program = rawJob._7,
              programName = rawJob._8,
              stdin = cleanNewlines(rawJob._9),
              expectedOut = cleanNewlines(rawJob._10)
            )
          } else { // never the less, create a new task, but now for higher stage
            val taskQuery = EvalTasks.filter(_.position === rawJob._13).filter(_.problemId === rawJob._1)
            Await.result(db.run(taskQuery.result.head) map { task =>
              Messages.NextStageJob(
                testrunId = rawJob._12,
                stage = rawJob._13.get,
                evalId = uuid,
                program = rawJob._7,
                stdin = cleanNewlines(rawJob._9),
                expectedOut = cleanNewlines(rawJob._10),
                progOut = cleanNewlines(rawJob._11.getOrElse("")),
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
        source ! JobsM(jobs.toList)
      }
  }
}
