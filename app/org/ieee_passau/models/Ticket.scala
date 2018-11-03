package org.ieee_passau.models

import java.util.Date

import org.ieee_passau.utils.LanguageHelper
import org.ieee_passau.utils.LanguageHelper.LangTypeMapper
import play.api.i18n.Lang
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{CompiledFunction, ProvenShape}

import scala.concurrent.Future

case class Ticket(id: Option[Int], problemId: Option[Int], userId: Option[Int], responseTo: Option[Int], text: String,
                  public: Boolean, created: Date, language: Lang) extends Entity[Ticket] {
  override def withId(id: Int): Ticket = this.copy(id = Some(id))
}

class Tickets(tag: Tag) extends TableWithId[Ticket](tag, "tickets") {
  def problemId: Rep[Int] = column[Int]("problem_id")
  def userId: Rep[Int] = column[Int]("user_id")
  def responseTo: Rep[Int] = column[Int]("parent_ticket")
  def text: Rep[String] = column[String]("ticket_text")
  def public: Rep[Boolean] = column[Boolean]("is_public")
  def created: Rep[Date] = column[Date]("created")(DateSupport.dateMapper)
  def language: Rep[Lang] = column[Lang]("lang")(LanguageHelper.LangTypeMapper)

  override def * : ProvenShape[Ticket] = (id.?, problemId.?, userId.?, responseTo.?, text, public, created, language) <> (Ticket.tupled, Ticket.unapply)
}

object Tickets extends TableQuery(new Tickets(_)) {
  def byId: CompiledFunction[Rep[Int] => Query[Tickets, Ticket, Seq], Rep[Int], Int, Query[Tickets, Ticket, Seq], Seq[Ticket]] =
    this.findBy(_.id)

  def update(id: Int, ticket: Ticket)(implicit db: Database): Future[Int] =
    db.run(this.byId(id).update(ticket.withId(id)))
}
