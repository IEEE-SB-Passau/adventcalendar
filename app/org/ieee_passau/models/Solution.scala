package org.ieee_passau.models

import java.util.Date

import org.ieee_passau.models.DateSupport.dateMapper
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape}

import scala.concurrent.Future

case class Solution (id: Option[Int], userId: Int, problemId: Int, language: String, program: String,
                     programName: String, ip: Option[String], userAgent: Option[String], browserId: Option[String],
                     created: Date) extends Entity[Solution] {
  override def withId(id: Int): Solution = this.copy(id = Some(id))
}

class Solutions(tag: Tag) extends TableWithId[Solution](tag, "solutions") {
  def userId: Rep[Int] = column[Int]("user_id")
  def problemId: Rep[Int] = column[Int]("problem_id")
  def language: Rep[String] = column[String]("language")
  def program: Rep[String] = column[String]("program")
  def programName: Rep[String] = column[String]("program_name")
  def ip: Rep[String] = column[String]("ip")
  def userAgent: Rep[String] = column[String]("user_agent")
  def browserId: Rep[String] = column[String]("browser_id")
  def created: Rep[Date] = column[Date]("created")(DateSupport.dateMapper)

  def user: ForeignKeyQuery[Users, User] = foreignKey("user_fk", userId, Users)(_.id)
  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id)

  override def * : ProvenShape[Solution] = (id.?, userId, problemId, language, program, programName, ip.?, userAgent.?,
    browserId.?, created) <> (Solution.tupled, Solution.unapply)
}

object Solutions extends TableQuery(new Solutions(_)) {
  def getLatestSolutionByUser(userId: Int)(implicit db: Database): Future[Option[Solution]] = {
    db.run(Solutions.filter(_.userId === userId).sortBy(_.created.desc).result.headOption)
  }
  def getLatestSolutionByUserAndProblem(userId: Int, problemId: Int)(implicit db: Database): Future[Option[Solution]] = {
    db.run(Solutions.filter(_.userId === userId).filter(_.problemId === problemId).sortBy(_.created.desc).result.headOption)
  }
}
