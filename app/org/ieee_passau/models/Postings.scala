package org.ieee_passau.models

import java.util.Date

import org.ieee_passau.models
import org.ieee_passau.models.DateSupport.dateMapper
import org.ieee_passau.utils.LanguageHelper
import org.ieee_passau.utils.LanguageHelper.LangTypeMapper
import play.api.i18n.Lang
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ProvenShape, TableQuery}

import scala.concurrent.{ExecutionContext, Future}

case class Posting(id: Option[Int], lang: Lang, title: String, content: String, date: Date) extends Entity[Posting] {
  override def withId(id: Int): Posting = this.copy(id = Some(id))
}

class Postings(tag: Tag) extends Table[Posting](tag, "postings") {
  def id: Rep[Int] = column[Int]("id")
  def lang: Rep[Lang] = column[Lang]("lang")(LanguageHelper.LangTypeMapper)
  def title: Rep[String] = column[String]("title")
  def content: Rep[String] = column[String]("content")
  def date: Rep[Date] = column[Date]("date")(DateSupport.dateMapper)

  override def * : ProvenShape[Posting] = (id.?, lang, title, content, date) <> (Posting.tupled, Posting.unapply)
}

object Page extends Enumeration {
  type Page = Value

  def byId(id: Int): Page = values.filter(_.id == id).head

  val status: models.Page.Value = Value(1, "status")
  val calendar: models.Page.Value = Value(2, "calendar")
  val news: models.Page.Value = Value(3, "news")
  val faq: models.Page.Value = Value(4, "faq")
  val rules: models.Page.Value = Value(5, "rules")
  val examples: models.Page.Value = Value(6, "examples")
  val contact: models.Page.Value = Value(7, "contact")
  val notification: models.Page.Value = Value(8, "notification")
}

object Postings extends TableQuery(new Postings(_)) {
  def byId(id: Int, preferredLang: Lang)(implicit db: Database, ec: ExecutionContext): Future[Posting] = {
    byIdOption(id, preferredLang).map(_.get)
  }

  def byIdOption(id: Int, preferredLang: Lang)(implicit db: Database, ec: ExecutionContext): Future[Option[Posting]] = {
    // cannot be inlined because type cannot be inferred
    val query: Query[(Rep[Option[Int]], Rep[Lang], Rep[String], Rep[String], Rep[Date]), (Option[Int], Lang, String, String, Date), Seq] = for {
      p <- Postings if p.id === id
    } yield (p.id.?, p.lang, p.title, p.content, p.date)
    db.run(query.result).map { postings =>
      // TODO: ordering would need to work on Rep[Lang] in order to sort in the database
      postings.sortBy(x => x._2 /*lang*/)(LanguageHelper.ordering(preferredLang))
        .map { p => Posting.tupled(p) }
    }.flatMap { postings => Future.successful(postings.headOption) }
  }

  def update(id: Int, lang: String, post: Posting)(implicit db: Database): Future[Int] =
    db.run(this.filter(p => p.id === id && p.lang === Lang(lang)).update(post.withId(id)))
}
