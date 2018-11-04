package org.ieee_passau.evaluation

import org.ieee_passau.models.Result

object Messages {

  // Entities/Beans

  case class Config(actorName: String,
                    host: String,
                    port: Int)

  trait Job {
    val testrunId: Int
    val evalId: String
    val stage: Int
    val program: String
    val programName: String
    val stdin: String
    val expectedOut: String

    override def toString: String = {
      "Job[id=%s]".format(this.testrunId)
    }

    override def hashCode(): Int = testrunId.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case job: Job => job.testrunId == testrunId && job.stage == stage
        case _ => false
      }
    }
  }

  case class BaseJob(cpuFactor: Long,
                     memFactor: Int,
                     lang: String,
                     testrunId: Int,
                     evalId: String,
                     program: String,
                     programName: String,
                     stdin: String,
                     expectedOut: String) extends Job {
    override val stage = 0
  }

  case class NextStageJob(testrunId: Int,
                          stage: Int,
                          evalId: String,
                          program: String,
                          stdin: String,
                          progOut: String,
                          expectedOut: String,
                          command: String,
                          inputData: (Boolean, Boolean, Boolean, Boolean),
                          outputStdoutCheck: Boolean,
                          outputScore: Boolean,
                          programName: String,
                          file: Array[Byte]) extends Job

  case class EvaluatedJob(job: Job,
                          progOut: Option[String],
                          progErr: Option[String],
                          progExit: Option[Int],
                          progRuntime: Option[Double],
                          progMemory: Option[Int],
                          compOut: Option[String],
                          compErr: Option[String],
                          compExit: Option[Int],
                          compRuntime: Option[Double],
                          compMemory: Option[Int],
                          score: Option[Int],
                          result: Option[Result]) {

    override def toString: String = {
      "EvaluatedJob[jobid=%s;result=%s]".format(this.job.evalId, this.result)
    }
  }

  // Messages
  case class EvaluatedJobM(eJob: EvaluatedJob)

  case class JobM(job: Job)

  case class JobsM(jobs: List[Job])

  case class ReadJobsDB(count: Int)

  case class NewVM(config: Config)

  case class RemoveVM(name: String)

  // Broadcast messages
  case class JobFailure(job: Job)

  case class JobFinished(job: Job)
}
