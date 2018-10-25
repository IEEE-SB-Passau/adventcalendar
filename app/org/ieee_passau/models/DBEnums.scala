package org.ieee_passau.models

import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{Session => _, _}

import scala.slick.ast.BaseTypedType
import scala.slick.jdbc.JdbcType
import scala.slick.lifted.ProvenShape

object Result {
  implicit val resultTypeMapper: JdbcType[Result] with BaseTypedType[Result] = MappedColumnType.base[Result, String] (
    r => r.name,
    s => Result(s)
  )
}
case class Result(name: String) {
  override def toString: String = name.toLowerCase
}
object Queued extends Result("QUEUED")
object Passed extends Result("PASSED")
object WrongAnswer extends Result("WRONG_ANSWER")
object ProgramError extends Result("PROGRAM_ERROR")
object MemoryExceeded extends Result("MEMORY_EXCEEDED")
object RuntimeExceeded extends Result("RUNTIME_EXCEEDED")
object CompileError extends Result("COMPILER_ERROR")
object Canceled extends Result("CANCELED")

class Results(tag: Tag) extends Table[Result](tag, "e_test_result") {
  def result: Column[Result] = column[Result]("result")
  def * : ProvenShape[Result] = result
}
object Results extends TableQuery(new Results(_))

case class Language(id: String, name: String, highlightClass: String, extension: String, cpuFactor: Float, memFactor: Float, comment: String)
class Languages(tag: Tag) extends Table[Language](tag, "e_prog_lang") {
  def id: Column[String] = column[String]("language")
  def name: Column[String] = column[String]("name")
  def highlightClass: Column[String] = column[String]("highlight_class")
  def extension: Column[String] = column[String]("extension")
  def cpuFactor: Column[Float] = column[Float]("cpu_factor")
  def memFactor: Column[Float] = column[Float]("mem_factor")
  def comment: Column[String] = column[String]("comment")

  def * : ProvenShape[Language] = (id, name, highlightClass, extension, cpuFactor, memFactor, comment) <> (Language.tupled, Language.unapply)
}
object Languages extends TableQuery(new Languages(_)) {
  def byLang(lang: String): Option[Language] = {
    DB.withSession { implicit session: Session =>
      this.filter(_.id === lang).firstOption
    }
  }
  def update(id: String, language: Language)(implicit session: Session): Int =
    this.filter(_.id === id).update(language)
}

object Visibility {
  implicit val visibilityTypeMapper: JdbcType[Visibility] with BaseTypedType[Visibility] = MappedColumnType.base[Visibility, String] (
    r => r.scope,
    s => Visibility(s)
  )
}
case class Visibility(scope: String) {
  override def toString: String = scope.toLowerCase
}
object Public extends Visibility("PUBLIC")
object Private extends Visibility("PRIVATE")
object Hidden extends Visibility("HIDDEN")

class Visibilities(tag: Tag) extends Table[Visibility](tag, "e_test_visibility") {
  def scope: Column[Visibility] = column[Visibility]("scope")
  def * : ProvenShape[Visibility] = scope
}
object Visibilities extends TableQuery(new Visibilities(_))

object EvalMode {
  implicit val evalModeTypeMapper: JdbcType[EvalMode] with BaseTypedType[EvalMode] = MappedColumnType.base[EvalMode, String] (
    r => r.mode,
    s => EvalMode(s)
  )
}
case class EvalMode(mode: String) {
  override def toString: String = mode.toLowerCase
}
object Static extends EvalMode("STATIC")
object Dynamic extends EvalMode("DYNAMIC")
object Best extends EvalMode("BEST")
object NoEval extends EvalMode("NO_EVAL")

class EvalModes(tag: Tag) extends Table[EvalMode](tag, "e_test_eval_mode") {
  def mode: Column[EvalMode] = column[EvalMode]("mode")
  def * : ProvenShape[EvalMode] = mode
}
object EvalModes extends TableQuery(new EvalModes(_))

object Permission {
  implicit val permissionTypeMapper: JdbcType[Permission] with BaseTypedType[Permission] = MappedColumnType.base[Permission, String] (
    r => r.name,
    s => Permission(s)
  )
}
case class Permission(name: String) {
  override def toString: String = name.toLowerCase
  def includes(level: Permission): Boolean = {
    if (level == Everyone) return true
    if (this == level) return true
    if (this == Moderator && level == Contestant) return true
    if (this == Admin && (level == Contestant || level == Moderator)) return true
    false
  }
}
object Everyone extends Permission("EVERYONE")
object Guest extends Permission("GUEST")
object Contestant extends Permission("CONTESTANT")
object Moderator extends Permission("MODERATOR")
object Admin extends Permission("ADMIN")
object Internal extends Permission("INTERNAL")

class Permissions(tag: Tag) extends Table[Permission](tag, "e_permission") {
  def name: Column[Permission] = column[Permission]("name")
  def * : ProvenShape[Permission] = name
}
object Permissions extends TableQuery(new Permissions(_))