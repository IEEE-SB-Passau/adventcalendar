package org.ieee_passau.models

import play.api.db.slick.Config.driver.simple._

import scala.slick.lifted.{ForeignKeyQuery, ProvenShape}

case class Feedback(id: Option[Int], userId: Int, rating: Int, pro: Option[String], con: Option[String],
                    freetext: Option[String])  extends Entity[Feedback] {
  override def withId(id: Int): Feedback = this.copy(id = Some(id))
}

class Feedbacks(tag: Tag) extends TableWithId[Feedback](tag, "feedback") {
  def userId: Column[Int] = column[Int]("user_id")
  def rating: Column[Int] = column[Int]("rating")
  def pro: Column[String] = column[String]("pro")
  def con: Column[String] = column[String]("con")
  def freetext: Column[String] = column[String]("freetext")

  def user: ForeignKeyQuery[Users, User] = foreignKey("user_fk", userId, Users)(_.id)

  override def * : ProvenShape[Feedback] = (id.?, userId, rating, pro.?, con.?, freetext.?) <> (Feedback.tupled, Feedback.unapply)
}
object Feedbacks extends TableQuery(new Feedbacks(_))
