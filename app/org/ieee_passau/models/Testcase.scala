package org.ieee_passau.models

import org.ieee_passau.utils.FutureHelper
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape}

import scala.concurrent.Await

case class Testcase (id: Option[Int], problemId: Int, position: Int, visibility: Visibility, input: String,
                     expectedOutput: String, points: Int) extends Entity[Testcase] {
  override def withId(id: Int): Testcase = this.copy(id = Some(id))
}

class Testcases(tag: Tag) extends TableWithId[Testcase](tag, "testcases") {
  def problemId: Rep[Int] = column[Int]("problem_id")
  def position: Rep[Int] = column[Int]("position")
  def visibility: Rep[Visibility] = column[Visibility]("visibility") (Visibility.visibilityTypeMapper)
  def input: Rep[String] = column[String]("input")
  def expectedOutput: Rep[String] = column[String]("expected_output")
  def points: Rep[Int] = column[Int]("points")

  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id)

  override def * : ProvenShape[Testcase] = (id.?, problemId, position, visibility, input, expectedOutput, points) <> (Testcase.tupled, Testcase.unapply)
}

object Testcases extends TableQuery(new Testcases(_)) {
  def byId: CompiledFunction[Rep[Int] => Query[Testcases, Testcase, Seq], Rep[Int], Int, Query[Testcases, Testcase, Seq], Seq[Testcase]] =
    this.findBy(_.id)

  def update(id: Int, testcase: Testcase)(implicit db: Database): DBIOAction[Int, NoStream, Effect.Write] =
    this.byId(id).update(testcase.withId(id))

  def isPositionAvailable(problemId: Int, position: Int)(implicit db: Database): Boolean =
    Await.result(db.run(Query(Testcases.filter(t => t.problemId === problemId && t.position === position).length).result), FutureHelper.dbTimeout).head == 0

  def isPositionAvailable(id: Int, problemId: Int, position: Int)(implicit db: Database): Boolean =
    Await.result(db.run(Query(Testcases.filter(t => !(t.id === id) && t.problemId === problemId && t.position === position).length).result), FutureHelper.dbTimeout).head == 0
}
