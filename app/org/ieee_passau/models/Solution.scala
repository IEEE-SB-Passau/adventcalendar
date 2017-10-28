package org.ieee_passau.models

import java.util.Date

import play.api.db.slick.Config.driver.simple._

import scala.slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape}

case class Solution (id: Option[Int], userId: Int, problemId: Int, language: String, program: String,
                     programName: String, ip: Option[String], userAgent: Option[String], browserId: Option[String],
                     created: Date) extends Entity[Solution] {
  override def withId(id: Int): Solution = this.copy(id = Some(id))
}

class Solutions(tag: Tag) extends TableWithId[Solution](tag, "solutions") {
  def userId: Column[Int] = column[Int]("user_id")
  def problemId: Column[Int] = column[Int]("problem_id")
  def language: Column[String] = column[String]("language")
  def program: Column[String] = column[String]("program")
  def programName: Column[String] = column[String]("program_name")
  def ip: Column[String] = column[String]("ip")
  def userAgent: Column[String] = column[String]("user_agent")
  def browserId: Column[String] = column[String]("browser_id")
  def created: Column[Date] = column[Date]("created")

  def user: ForeignKeyQuery[Users, User] = foreignKey("user_fk", userId, Users)(_.id)
  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id)

  override def * : ProvenShape[Solution] = (id.?, userId, problemId, language, program, programName, ip.?, userAgent.?,
    browserId.?, created) <> (Solution.tupled, Solution.unapply)
}

object Solutions extends TableQuery(new Solutions(_)) {
  def byProblemId: CompiledFunction[(Column[Int]) => Query[Solutions, Solution, Seq], Column[Int], Int, Query[Solutions, Solution, Seq], Seq[Solution]] =
    this.findBy(_.problemId)
}
