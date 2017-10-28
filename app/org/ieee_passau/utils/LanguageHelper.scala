package org.ieee_passau.utils

import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{Session => _}
import play.api.i18n.Lang

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

  val defaultLanguage = Lang(play.Configuration.root().getString("application.langs").split(",")(0))

  implicit val LangTypeMapper: JdbcType[Lang] with BaseTypedType[Lang] = MappedColumnType.base[Lang, String](
    l => l.code,
    c => if (c.isEmpty) defaultLanguage else Lang(c)
  )
}
