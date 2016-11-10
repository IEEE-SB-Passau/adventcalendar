package org.ieee_passau.models

import play.api.db.slick.Config.driver.simple._

import scala.slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape}

case class Testcase (id: Option[Int], problemId: Int, position: Int, visibility: Visibility, input: String,
                     expectedOutput: String, points: Int) extends Entity[Testcase] {
  override def withId(id: Int): Testcase = this.copy(id = Some(id))
}

class Testcases(tag: Tag) extends TableWithId[Testcase](tag, "testcases") {
  def problemId: Column[Int] = column[Int]("problem_id")
  def position: Column[Int] = column[Int]("position")
  def visibility: Column[Visibility] = column[Visibility]("visibility") (Visibility.visibilityTypeMapper)
  def input: Column[String] = column[String]("input")
  def expectedOutput: Column[String] = column[String]("expected_output") //references e_test_visibility (Visibility) on update restrict on delete restrict
  def points: Column[Int] = column[Int]("points")

  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id) // references problems (id) on update cascade on delete cascade

  override def * : ProvenShape[Testcase] = (id.?, problemId, position, visibility, input, expectedOutput, points) <> (Testcase.tupled, Testcase.unapply)
}

object Testcases extends TableQuery(new Testcases(_)) {
  def byProblemId: CompiledFunction[(Column[Int]) => Query[Testcases, Testcase, Seq], Column[Int], Int, Query[Testcases, Testcase, Seq], Seq[Testcase]] = this.findBy(_.problemId)
  def byId: CompiledFunction[(Column[Int]) => Query[Testcases, Testcase, Seq], Column[Int], Int, Query[Testcases, Testcase, Seq], Seq[Testcase]] = this.findBy(_.id)
  def update(id: Int, testcase: Testcase)(implicit session: Session): Int = this.filter(_.id === id).update(testcase.withId(id))
}
