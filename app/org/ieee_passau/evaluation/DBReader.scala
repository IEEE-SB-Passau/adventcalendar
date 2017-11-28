package org.ieee_passau.evaluation

import java.util.UUID

import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.StringHelper._
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._

/**
  * If prompted by InputRegulator, reads jobs from the database and sends them to InputRegulator.
  */
class DBReader extends EvaluationActor {

  override def receive: Receive = {
    case ReadJobsDB(count) =>
      log.debug("DBReader received request for %d jobs".format(count))

      DB.withSession { implicit session =>
        val q = for {
          tr <- Testruns if tr.stage.?.isDefined // || tr.result === (Queued: Result)
          tc <- Testcases if tc.id === tr.testcaseId
          sl <- Solutions if sl.id === tr.solutionId
          pr <- Problems if pr.id === sl.problemId
        } yield (tr.created, tr.id, sl.language, sl.program, tc.input, tc.expectedOutput, pr.id, tr, sl.programName)

        val rawJobs = q.sortBy(_._1.asc).take(count).list
        val jobs = rawJobs.map { rawJob =>
          val stage = rawJob._8/*testrun*/.stage.get
          val uuid = UUID.randomUUID().toString
          if (stage == 0) { // normal evaluation job
            Messages.BaseJob(
              problemId = rawJob._7,
              testrunId = rawJob._2,
              evalId = uuid,
              language = rawJob._3,
              program = rawJob._4,
              programName = rawJob._9,
              stdin = cleanNewlines(rawJob._5),
              expectedOut = cleanNewlines(rawJob._6)
            )
          } else { // never the less, create a new task, but now for higher stage
            val task = EvalTasks.filter(_.position === stage).filter(_.problemId === rawJob._7).first
            Messages.NextStageJob(
              testrunId = rawJob._2,
              stage = stage,
              evalId = uuid,
              program = rawJob._4,
              stdin = cleanNewlines(rawJob._5),
              expectedOut = cleanNewlines(rawJob._6),
              progOut = rawJob._8.progOut.get,
              command = task.command,
              inputData = (task.useStdin, task.useProgout, task.useExpout, task.useProgram),
              outputStdoutCheck = task.outputCheck,
              outputScore = task.scoreCalc,
              programName = task.filename,
              file = task.file
            )
          }
        }

        log.debug("DBReader is sending %d jobs to InputRegulator".format(jobs.length))
        sender ! JobsM(jobs)
      }
  }
}
