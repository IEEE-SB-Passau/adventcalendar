package org.ieee_passau.models

import java.util.Date

import play.api.db.slick.Config.driver.simple._

import scala.slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape}

case class Testrun(id: Option[Int], solutionId: Int, testcaseId: Int, progOut: Option[String],
                   progErr: Option[String], progExit: Option[Int], progRuntime: Option[Double],
                   compOut: Option[String], compErr: Option[String], compExit: Option[Int],
                   compRuntime: Option[Double], result: Result, score: Option[Int], created: Date,
                   stage: Option[Int], vm: Option[String], completed: Date) extends Entity[Testrun] {
  override def withId(id: Int): Testrun = this.copy(id = Some(id))
}

class Testruns(tag: Tag) extends TableWithId[Testrun](tag, "testruns") {
  def solutionId: Column[Int] = column[Int]("solution_id")
  def testcaseId: Column[Int] = column[Int]("testcase_id")
  def progOut: Column[String] = column[String]("prog_out")
  def progErr: Column[String] = column[String]("prog_err")
  def progExit: Column[Int] = column[Int]("prog_exit")
  def progRuntime: Column[Double] = column[Double]("prog_runtime")
  def compOut: Column[String] = column[String]("comp_out")
  def compErr: Column[String] = column[String]("comp_err")
  def compExit: Column[Int] = column[Int]("comp_exit")
  def compRuntime: Column[Double] = column[Double]("comp_runtime")
  def result: Column[Result] = column[Result]("result")(Result.resultTypeMapper)
  def created: Column[Date] = column[Date]("created")
  def stage: Column[Int] = column[Int]("stage")
  def vm: Column[String] = column[String]("vm")
  def completed: Column[Date] = column[Date]("completed")
  def score: Column[Int] = column[Int]("score")

  def solution: ForeignKeyQuery[Solutions, Solution] = foreignKey("solution_fk", solutionId, Solutions)(_.id)
  def testcase: ForeignKeyQuery[Testcases, Testcase] = foreignKey("testcase_fk", testcaseId, Testcases)(_.id)

  override def * : ProvenShape[Testrun] = (id.?, solutionId, testcaseId,
    progOut.?, progErr.?, progExit.?, progRuntime.?, compOut.?, compErr.?, compExit.?, compRuntime.?,
    result, score.?, created, stage.?, vm.?, completed) <> (Testrun.tupled, Testrun.unapply)
}

object Testruns extends TableQuery(new Testruns(_)) {
  def bySolutionId: CompiledFunction[(Column[Int]) => Query[Testruns, Testrun, Seq], Column[Int], Int, Query[Testruns, Testrun, Seq], Seq[Testrun]] =
    this.findBy(_.solutionId)
  def byId: CompiledFunction[(Column[Int]) => Query[Testruns, Testrun, Seq], Column[Int], Int, Query[Testruns, Testrun, Seq], Seq[Testrun]] =
    this.findBy(_.id)
  def update(id: Int, testrun: Testrun)(implicit session: Session): Int =
    this.filter(_.id === id).update(testrun.withId(id))
}
