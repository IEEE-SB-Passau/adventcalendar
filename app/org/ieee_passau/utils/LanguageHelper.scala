package org.ieee_passau.utils

import play.api.i18n._
import play.api.inject.guice.{GuiceApplicationLoader, GuiceableModule}
import play.api.{ApplicationLoader, Configuration}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object LanguageHelper {

  private[utils] var config: Configuration = _

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

  lazy val langs: List[Lang] = config.underlying.getStringList("play.i18n.langs").asScala.map(Lang(_)).toList
  lazy val defaultLanguage: Lang = langs.head
  def orderedLangs(preferredLang: Lang): List[Lang] = langs.sorted(LanguageHelper.ordering(preferredLang))

  implicit lazy val LangTypeMapper: JdbcType[Lang] with BaseTypedType[Lang] = MappedColumnType.base[Lang, String](
    l => l.code,
    c => if (c.isEmpty) defaultLanguage else Lang(c)
  )
}

class CustomApplicationLoader extends GuiceApplicationLoader() {
  override def overrides(context: ApplicationLoader.Context): Seq[GuiceableModule] = {
    val ret = super.overrides(context)
    LanguageHelper.config = context.initialConfiguration
    ret
  }
}
