package org.ieee_passau.models

import slick.ast.BaseTypedType
import slick.dbio.Effect
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import scala.concurrent.Future

object Result {
  implicit val resultTypeMapper: JdbcType[Result] with BaseTypedType[Result] = MappedColumnType.base[Result, String] (
    { r => r.name },
    { s => Result(s) }
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
  def result: Rep[Result] = column[Result]("result")
  def * : ProvenShape[Result] = result
}
object Results extends TableQuery(new Results(_))

case class Language(id: String, name: String, highlightClass: String, extension: String, cpuFactor: Float, memFactor: Float, comment: String)
class Languages(tag: Tag) extends Table[Language](tag, "e_prog_lang") {
  def id: Rep[String] = column[String]("language")
  def name: Rep[String] = column[String]("name")
  def highlightClass: Rep[String] = column[String]("highlight_class")
  def extension: Rep[String] = column[String]("extension")
  def cpuFactor: Rep[Float] = column[Float]("cpu_factor")
  def memFactor: Rep[Float] = column[Float]("mem_factor")
  def comment: Rep[String] = column[String]("comment")

  def * : ProvenShape[Language] = (id, name, highlightClass, extension, cpuFactor, memFactor, comment) <> (Language.tupled, Language.unapply)
}
object Languages extends TableQuery(new Languages(_)) {
  def byLang(lang: String)(implicit db: Database): Future[Option[Language]] = db.run(this.filter(_.id === lang).result.headOption)

  def update(id: String, lang: Language): DBIOAction[Int, NoStream, Effect.Write] =
    this.filter(_.id === id).update(lang)
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
  def scope: Rep[Visibility] = column[Visibility]("scope")
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
  def mode: Rep[EvalMode] = column[EvalMode]("mode")
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
  def name: Rep[Permission] = column[Permission]("name")
  def * : ProvenShape[Permission] = name
}
object Permissions extends TableQuery(new Permissions(_))
