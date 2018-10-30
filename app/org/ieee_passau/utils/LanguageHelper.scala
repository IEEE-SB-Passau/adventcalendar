package org.ieee_passau.utils

import com.google.inject.AbstractModule
import javax.inject.Inject
import play.api.i18n._
import play.api.{Configuration, Environment}
import slick.ast.BaseTypedType
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcType

import scala.language.implicitConversions
import collection.JavaConverters._

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

class MyMessageApi @Inject() (environment: Environment, configuration: Configuration, langs: Langs)
  extends DefaultMessagesApi(environment, configuration, langs) {

  override protected def loadAllMessages: Map[String, Map[String, String]] = {
    langs.availables.map(_.code).map { lang =>
      (lang, loadMessages("messages_" + lang + ".properties"))
    }.toMap
      .+("default" -> loadMessages("messages"))
      .+("default.play" -> loadMessages("messages.default"))
  }
}

class MessageModule extends AbstractModule {
  def configure(): Unit = {
    bind(classOf[Langs]).to(classOf[DefaultLangs])
    bind(classOf[MessagesApi]).to(classOf[MyMessageApi])
  }
}