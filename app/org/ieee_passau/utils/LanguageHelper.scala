package org.ieee_passau.utils

import play.api.i18n._
import slick.ast.BaseTypedType
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcType

import scala.collection.JavaConverters._
import scala.language.implicitConversions

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

  // TODO get a DI version of the config?
  val langs: List[Lang] = play.Configuration.root().getStringList("play.i18n.langs").asScala.map(
    l => Lang(l)
  ).toList

  def orderedLangs(preferredLang: Lang): List[Lang] = langs.sorted(LanguageHelper.ordering(preferredLang))

  val defaultLanguage = Lang(play.Configuration.root().getStringList("play.i18n.langs").get(0))

  implicit val LangTypeMapper: JdbcType[Lang] with BaseTypedType[Lang] = MappedColumnType.base[Lang, String](
    l => l.code,
    c => if (c.isEmpty) defaultLanguage else Lang(c)
  )
}