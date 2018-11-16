package org.ieee_passau.models

import java.util.Date

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape}

import scala.concurrent.Future

case class Testrun(id: Option[Int], solutionId: Int, testcaseId: Int, progOut: Option[String], progErr: Option[String],
                   progExit: Option[Int], progRuntime: Option[Double], progMemory: Option[Int], compOut: Option[String],
                   compErr: Option[String], compExit: Option[Int], compRuntime: Option[Double], compMemory: Option[Int],
                   result: Result, score: Option[Int], created: Date, stage: Option[Int], vm: Option[String],
                   evalId: Option[String], completed: Date) extends Entity[Testrun] {
  override def withId(id: Int): Testrun = this.copy(id = Some(id))
}

class Testruns(tag: Tag) extends TableWithId[Testrun](tag, "testruns") {
  def solutionId: Rep[Int] = column[Int]("solution_id")
  def testcaseId: Rep[Int] = column[Int]("testcase_id")
  def progOut: Rep[Option[String]] = column[Option[String]]("prog_out")
  def progErr: Rep[Option[String]] = column[Option[String]]("prog_err")
  def progExit: Rep[Option[Int]] = column[Option[Int]]("prog_exit")
  def progRuntime: Rep[Option[Double]] = column[Option[Double]]("prog_runtime")
  def progMemory: Rep[Option[Int]] = column[Option[Int]]("prog_memory")
  def compOut: Rep[Option[String]] = column[Option[String]]("comp_out")
  def compErr: Rep[Option[String]] = column[Option[String]]("comp_err")
  def compExit: Rep[Option[Int]] = column[Option[Int]]("comp_exit")
  def compRuntime: Rep[Option[Double]] = column[Option[Double]]("comp_runtime")
  def compMemory: Rep[Option[Int]] = column[Option[Int]]("comp_memory")
  def result: Rep[Result] = column[Result]("result")(Result.resultTypeMapper)
  def created: Rep[Date] = column[Date]("created")(DateSupport.dateMapper)
  def stage: Rep[Option[Int]] = column[Option[Int]]("stage")
  def vm: Rep[Option[String]] = column[Option[String]]("vm")
  def completed: Rep[Date] = column[Date]("completed")(DateSupport.dateMapper)
  def score: Rep[Option[Int]] = column[Option[Int]]("score")
  def evalId: Rep[Option[String]] = column[Option[String]]("eval_id")

  def solution: ForeignKeyQuery[Solutions, Solution] = foreignKey("solution_fk", solutionId, Solutions)(_.id)
  def testcase: ForeignKeyQuery[Testcases, Testcase] = foreignKey("testcase_fk", testcaseId, Testcases)(_.id)

  override def * : ProvenShape[Testrun] = (id.?, solutionId, testcaseId,
    progOut, progErr, progExit, progRuntime, progMemory,
    compOut, compErr, compExit, compRuntime, compMemory,
    result, score, created, stage, vm, evalId, completed) <> (Testrun.tupled, Testrun.unapply)
}

object Testruns extends TableQuery(new Testruns(_)) {
  def bySolutionIdTestcaseId(solutionId: Int, testcaseId: Int): Query[Testruns, Testrun, Seq] =
    filter(r => r.solutionId === solutionId && r.testcaseId === testcaseId)
  def byId: CompiledFunction[Rep[Int] => Query[Testruns, Testrun, Seq], Rep[Int], Int, Query[Testruns, Testrun, Seq], Seq[Testrun]] =
    this.findBy(_.id)

  def update(id: Int, testrun: Testrun)(implicit db: Database): Future[Int] =
    db.run(this.byId(id).update(testrun.withId(id)))
}
