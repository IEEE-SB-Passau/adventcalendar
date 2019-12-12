package org.ieee_passau.models

import java.util.Date

import org.ieee_passau.models.DateSupport.dateMapper
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape}
import slick.sql.{FixedSqlAction, SqlAction}

import scala.concurrent.Future

case class Solution(id: Option[Int], userId: Int, problemId: Int, languageId: String, program: String,
                    programName: String, created: Date, score: Int, result: Result) extends Entity[Solution] {
  override def withId(id: Int): Solution = this.copy(id = Some(id))
}

class Solutions(tag: Tag) extends TableWithId[Solution](tag, "solutions") {
  def userId: Rep[Int] = column[Int]("user_id")
  def problemId: Rep[Int] = column[Int]("problem_id")
  def languageId: Rep[String] = column[String]("language")
  def program: Rep[String] = column[String]("program")
  def programName: Rep[String] = column[String]("program_name")
  def created: Rep[Date] = column[Date]("created")(DateSupport.dateMapper)
  def score: Rep[Int] = column[Int]("score")
  def result: Rep[Result] = column[Result]("result")(Result.resultTypeMapper)

  def user: ForeignKeyQuery[Users, User] = foreignKey("user_fk", userId, Users)(_.id)
  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id)
  def language: ForeignKeyQuery[Languages, Language] = foreignKey("language_fk", languageId, Languages)(_.id)

  override def * : ProvenShape[Solution] = (id.?, userId, problemId, languageId, program, programName, created, score, result) <> (Solution.tupled, Solution.unapply)
}

object Solutions extends TableQuery(new Solutions(_)) {
  def getLatestSolutionByUser(userId: Int)(implicit db: Database): SqlAction[Option[Solution], NoStream, Effect.Read] = {
    Solutions.filter(_.userId === userId).sortBy(_.created.desc).result.headOption
  }
  def getLatestSolutionByUserAndProblem(userId: Int, problemId: Int): SqlAction[Option[Solution], NoStream, Effect.Read] = {
    Solutions.filter(_.userId === userId).filter(_.problemId === problemId).sortBy(_.created.desc).result.headOption
  }

  def update(id: Int, solution: Solution): FixedSqlAction[Int, NoStream, Effect.Write] =
    this.filter(_.id === id).update(solution.withId(id))
}
