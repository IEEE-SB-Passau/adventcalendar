package org.ieee_passau.utils

import play.api.Application
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{Session => _}
import play.api.i18n.{DefaultMessagesPlugin, Lang}

import scala.language.implicitConversions
import scala.slick.ast.BaseTypedType
import scala.slick.jdbc.JdbcType

object LanguageHelper {

  implicit def ordering[A <: Lang](preferred: Lang): Ordering[A] = new Ordering[A] {
    override def compare(x: A, y: A): Int = {
      if (x == y) return 0
      if (x.equals(preferred)) return -1
      if (y.equals(preferred)) return 1
      if (x.equals(defaultLanguage)) return 1
      if (y.equals(defaultLanguage)) return -1
      x.code.compareTo(y.code)
    }
  }

  val langs: List[Lang] = play.Configuration.root().getString("application.langs").split(",").map(
    l => Lang(l)
  ).toList

  def orderedLangs(preferredLang: Lang): List[Lang] = langs.sorted(LanguageHelper.ordering(preferredLang))

  val defaultLanguage = Lang(play.Configuration.root().getString("application.langs").split(",")(0))

  implicit val LangTypeMapper: JdbcType[Lang] with BaseTypedType[Lang] = MappedColumnType.base[Lang, String](
    l => l.code,
    c => if (c.isEmpty) defaultLanguage else Lang(c)
  )
}

class customMessagePlugin(app: Application) extends DefaultMessagesPlugin(app = app) {

  private lazy val pluginEnabled = app.configuration.getString("custommessagesplugin")

  override protected def messages = {
    Lang.availables(app).map(_.code).map { lang =>
      (lang, loadMessages("messages_" + lang + ".properties"))
    }.toMap
      .+("default" -> loadMessages("messages"))
      .+("default.play" -> loadMessages("messages.default"))
  }
}
