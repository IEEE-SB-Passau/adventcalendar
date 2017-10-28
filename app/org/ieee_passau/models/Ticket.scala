package org.ieee_passau.models

import java.util.Date

import play.api.db.slick.Config.driver.simple._

import scala.slick.lifted.{CompiledFunction, ProvenShape}

case class Ticket(id: Option[Int], problemId: Option[Int], userId: Option[Int], responseTo: Option[Int], text: String,
                  public: Boolean, created: Date) extends Entity[Ticket] {
  override def withId(id: Int): Ticket = this.copy(id = Some(id))
}

class Tickets(tag: Tag) extends TableWithId[Ticket](tag, "tickets") {
  def problemId: Column[Int] = column[Int]("problem_id")
  def userId: Column[Int] = column[Int]("user_id")
  def responseTo: Column[Int] = column[Int]("parent_ticket")
  def text: Column[String] = column[String]("ticket_text")
  def public: Column[Boolean] = column[Boolean]("is_public")
  def created: Column[Date] = column[Date]("created")

  override def * : ProvenShape[Ticket] = (id.?, problemId.?, userId.?, responseTo.?, text, public, created) <> (Ticket.tupled, Ticket.unapply)
}

object Tickets extends TableQuery(new Tickets(_)) {
  def byProblemId: CompiledFunction[(Column[Int]) => Query[Tickets, Ticket, Seq], Column[Int], Int, Query[Tickets, Ticket, Seq], Seq[Ticket]] =
    this.findBy(_.problemId)
  def byId: CompiledFunction[(Column[Int]) => Query[Tickets, Ticket, Seq], Column[Int], Int, Query[Tickets, Ticket, Seq], Seq[Ticket]] =
    this.findBy(_.id)
  def update(id: Int, ticket: Ticket)(implicit session: Session): Int =
    this.filter(_.id === id).update(ticket.withId(id))
}
