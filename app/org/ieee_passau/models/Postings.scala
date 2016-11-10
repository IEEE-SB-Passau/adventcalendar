package org.ieee_passau.models

import java.util.Date

import play.api.db.slick.Config.driver.simple._

import scala.slick.lifted.{CompiledFunction, ProvenShape}

case class Posting(id: Option[Int], title: String, content:String, date: Date)  extends Entity[Posting] {
  override def withId(id: Int): Posting = this.copy(id = Some(id))
}

class Postings(tag: Tag) extends TableWithId[Posting](tag, "postings") {
  def title: Column[String] = column[String]("title")
  def content: Column[String] = column[String]("content")
  def date: Column[Date] = column[Date]("date")

  override def * : ProvenShape[Posting] = (id.?, title, content, date) <> (Posting.tupled, Posting.unapply)
}
object Postings extends TableQuery(new Postings(_)) {
  def byId: CompiledFunction[(Column[Int]) => Query[Postings, Posting, Seq], Column[Int], Int, Query[Postings, Posting, Seq], Seq[Posting]] = this.findBy(_.id)
  def update(id: Int, posting: Posting)(implicit session: Session): Int = this.filter(_.id === id).update(posting.withId(id))
}
