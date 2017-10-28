package org.ieee_passau.models

import java.util.Date

import org.ieee_passau.utils.LanguageHelper
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{DB, Session}
import play.api.i18n.Lang

import scala.slick.lifted.{CompiledFunction, ProvenShape}

case class Posting(id: Option[Int], lang: Lang, title: String, content:String, date: Date)  extends Entity[Posting] {
  override def withId(id: Int): Posting = this.copy(id = Some(id))
}

class Postings(tag: Tag) extends Table[Posting](tag, "postings") {
  def id: Column[Int] = column[Int]("id")
  def lang: Column[Lang] = column[Lang]("lang")(LanguageHelper.LangTypeMapper)
  def title: Column[String] = column[String]("title")
  def content: Column[String] = column[String]("content")
  def date: Column[Date] = column[Date]("date")(DateSupport.dateMapper)

  override def * : ProvenShape[Posting] = (id.?, lang, title, content, date) <> (Posting.tupled, Posting.unapply)
}

object Page extends Enumeration {
  type Page = Value

  val news = Value(1, "news")
  val calendar = Value(2, "calendar")
  val status = Value(3, "status")
  val faq = Value(4, "faq")
  val rules = Value(5, "rules")
  val examples = Value(6, "examples")
  val contact = Value(7, "contact")
}

object Postings extends TableQuery(new Postings(_)) {
  implicit private def mapper =  LanguageHelper.LangTypeMapper

  def byId(id: Int, preferredLang: Lang): List[Posting] = DB.withSession { implicit session: Session =>
    (for {
      p <- Postings if p.id === id
    } yield (p.id.?, p.lang, p.title, p.content, p.date)
      ).list.sortBy(_._2 /*lang*/)(LanguageHelper.ordering(preferredLang))
      .map(p => Posting.tupled(p))
  }
  def byIdLang(id: Int, lang: String): Query[Postings, Posting, Seq] = this.filter(p => p.id === id && p.lang === Lang(lang))

  def list(preferredLang: Lang): Map[Int, List[Posting]] = DB.withSession { implicit session: Session =>
    (for {
      p <- Postings
    } yield (p.id.?, p.lang, p.title, p.content, p.date)
      ).list.groupBy(_._1 /*id*/).map(l =>
      (l._1.get,
        l._2.sortBy(_._2 /*lang*/)(LanguageHelper.ordering(preferredLang))
          .map(p => Posting.tupled(p))
      )
    )
  }

  def update(id: Int, lang: String, posting: Posting)(implicit session: Session): Int =
    this.filter(p => p.id === id && p.lang === Lang(lang)).update(posting.withId(id))
}
