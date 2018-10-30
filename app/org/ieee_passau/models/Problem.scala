package org.ieee_passau.models

import java.util.Date

import org.ieee_passau.utils.LanguageHelper
import org.ieee_passau.utils.LanguageHelper.LangTypeMapper
import play.api.i18n.Lang
import slick.dbio.Effect
import slick.driver.PostgresDriver.api._
import slick.lifted.{CompiledFunction, CompiledStreamingExecutable, ForeignKeyQuery, ProvenShape, TableQuery}

import scala.concurrent.{ExecutionContext, Future}

case class Problem (id: Option[Int], title: String, door: Int, description: String, readableStart: Date,
                    readableStop: Date, solvableStart: Date, solvableStop: Date, evalMode: EvalMode,
                    cpuFactor: Float, memFactor: Float) extends Entity[Problem] {
  def withId(id: Int): Problem = this.copy(id = Some(id))
  def readable: Boolean = {
    val now = new Date()
    readableStart.before(now) && readableStop.after(now)
  }
  def solvable: Boolean = {
    val now = new Date()
    solvableStart.before(now) && solvableStop.after(now)
  }
}

class Problems(tag: Tag) extends TableWithId[Problem](tag, "problems") {
  def title: Rep[String] = column[String]("title")
  def door: Rep[Int] = column[Int]("door")
  def description: Rep[String] = column[String]("description")
  def readableStart: Rep[Date] = column[Date]("readable_start")(DateSupport.dateMapper)
  def readableStop: Rep[Date] = column[Date]("readable_stop")(DateSupport.dateMapper)
  def solvableStart: Rep[Date] = column[Date]("solvable_start")(DateSupport.dateMapper)
  def solvableStop: Rep[Date] = column[Date]("solvable_stop")(DateSupport.dateMapper)
  def evalMode: Rep[EvalMode] = column[EvalMode]("eval_mode")(EvalMode.evalModeTypeMapper)
  def cpuFactor: Rep[Float] = column[Float]("cpu_factor")
  def memFactor: Rep[Float] = column[Float]("mem_factor")

  def * : ProvenShape[Problem] = (id.?, title, door, description, readableStart, readableStop, solvableStart,
    solvableStop, evalMode, cpuFactor, memFactor) <> (Problem.tupled, Problem.unapply)
}

object Problems extends TableQuery(new Problems(_)) {
  def byDoor: CompiledFunction[Rep[Int] => Query[Problems, Problem, Seq], Rep[Int], Int, Query[Problems, Problem, Seq], Seq[Problem]] =
    this.findBy(_.door)
  def byId: CompiledFunction[Rep[Int] => Query[Problems, Problem, Seq], Rep[Int], Int, Query[Problems, Problem, Seq], Seq[Problem]] =
    this.findBy(_.id)
  def update(id: Int, problem: Problem): DBIOAction[Int, NoStream, Effect.Write] =
    this.byId(id).update(problem.withId(id))

  def doorAvailable(door: Int, id: Int)(implicit db: Database, ec: ExecutionContext): Future[Boolean] =
    db.run(Problems.filter(_.door === door).result).map(result => !result.exists(problem => problem.id.get == id))
  def doorAvailable(door: Int)(implicit db: Database, ec: ExecutionContext): Future[Boolean] =
    db.run(Problems.filter(_.door === door).result).map(result => result.isEmpty)
}

case class ProblemTranslation(problemId: Int, language: Lang, title: String, description: String)

class ProblemTranslations(tag: Tag) extends Table[ProblemTranslation](tag, "problem_translations") {
  def problemId: Rep[Int] = column[Int]("problem_id")
  def lang: Rep[Lang] = column[Lang]("language_code")(LanguageHelper.LangTypeMapper)
  def title: Rep[String] = column[String]("title")
  def description: Rep[String] = column[String]("description")

  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id)

  override def * : ProvenShape[ProblemTranslation] = (problemId, lang, title, description) <> (ProblemTranslation.tupled, ProblemTranslation.unapply)
}

object ProblemTranslations extends TableQuery(new ProblemTranslations(_)){
  def byProblemLang(problemId: Int, lang: Lang): CompiledStreamingExecutable[Query[ProblemTranslations, ProblemTranslation, Seq], Seq[ProblemTranslation], ProblemTranslation] =
    Compiled(this.filter(t => t.problemId === problemId && t.lang === lang))
  def byProblemLang(problemId: Int, lang: String): CompiledStreamingExecutable[Query[ProblemTranslations, ProblemTranslation, Seq], Seq[ProblemTranslation], ProblemTranslation] =
    Compiled(this.filter(t => t.problemId === problemId && t.lang === Lang(lang)))

  def update(lang: String, problemTranslation: ProblemTranslation): DBIOAction[Int, NoStream, Effect.Write] =
    this.filter(t => t.problemId === problemTranslation.problemId && t.lang === Lang(lang)).update(problemTranslation)
}
