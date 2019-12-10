package org.ieee_passau.models

import java.util.Date

import org.ieee_passau.utils.LanguageHelper
import org.ieee_passau.utils.LanguageHelper.LangTypeMapper
import play.api.i18n.Lang
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape, TableQuery}

import scala.concurrent.{ExecutionContext, Future}

case class Problem (id: Option[Int], title: String, door: Int, description: String, readableStart: Date,
                    readableStop: Date, solvableStart: Date, solvableStop: Date, evalMode: EvalMode,
                    cpuFactor: Float, memFactor: Float, points: Int) extends Entity[Problem] {
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
  def door: Rep[Int] = column[Int]("door")
  def readableStart: Rep[Date] = column[Date]("readable_start")(DateSupport.dateMapper)
  def readableStop: Rep[Date] = column[Date]("readable_stop")(DateSupport.dateMapper)
  def solvableStart: Rep[Date] = column[Date]("solvable_start")(DateSupport.dateMapper)
  def solvableStop: Rep[Date] = column[Date]("solvable_stop")(DateSupport.dateMapper)
  def evalMode: Rep[EvalMode] = column[EvalMode]("eval_mode")(EvalMode.evalModeTypeMapper)
  def cpuFactor: Rep[Float] = column[Float]("cpu_factor")
  def memFactor: Rep[Float] = column[Float]("mem_factor")
  def points: Rep[Int] = column[Int]("points")

  def * : ProvenShape[Problem] = (id.?, door, readableStart, readableStop, solvableStart,
    solvableStop, evalMode, cpuFactor, memFactor, points) <> (rowToProblem, problemToRow)

  private val rowToProblem: ((Option[Int], Int, Date, Date, Date, Date, EvalMode, Float, Float, Int)) => Problem = {
    case (id, door, readableStart, readableStop, solvableStart,
    solvableStop, evalMode, cpuFactor, memFactor, points) => Problem(id, "", door, "", readableStart, readableStop, solvableStart,
      solvableStop, evalMode, cpuFactor, memFactor, points)
  }

  private val problemToRow: Problem => Option[(Option[Int], Int, Date, Date, Date, Date, EvalMode, Float, Float, Int)] = {
    case Problem(id, _, door, _, readableStart, readableStop, solvableStart, solvableStop, evalMode, cpuFactor, memFactor, points) =>
      Some(id, door, readableStart, readableStop, solvableStart, solvableStop, evalMode, cpuFactor, memFactor, points)
  }
}

object Problems extends TableQuery(new Problems(_)) {
  def byDoor: CompiledFunction[Rep[Int] => Query[Problems, Problem, Seq], Rep[Int], Int, Query[Problems, Problem, Seq], Seq[Problem]] =
    this.findBy(_.door)
  def byId: CompiledFunction[Rep[Int] => Query[Problems, Problem, Seq], Rep[Int], Int, Query[Problems, Problem, Seq], Seq[Problem]] =
    this.findBy(_.id)

  def update(id: Int, problem: Problem)(implicit db: Database, ec: ExecutionContext): Future[Int] =
    db.run(Testcases.filter(_.problemId === id).map(_.points).sum.result.flatMap(
      points => Problems.filter(_.id === id).update(problem.withId(id).copy(points = points.getOrElse(0)))
    ))

  def doorAvailable(door: Int, id: Int)(implicit db: Database, ec: ExecutionContext): Future[Boolean] =
    db.run(Problems.filter(_.door === door).result).map(result => !result.exists(problem => problem.id.get != id))
  def doorAvailable(door: Int)(implicit db: Database, ec: ExecutionContext): Future[Boolean] =
    db.run(Problems.filter(_.door === door).result).map(result => result.isEmpty)

  def reeval(pid: Int)(implicit ec: ExecutionContext): DBIOAction[Unit, NoStream, Effect.Read with Effect.Write] = {
    for {
      solutions <- Solutions.filter(_.problemId === pid).map(_.id).result
      _ <- Solutions.filter(_.problemId === pid).map(sl => (sl.score, sl.result)).update(0, Queued)
      _ <- Testruns.filter(_.solutionId inSet solutions)
        .map(tr => (tr.result, tr.stage, tr.vm, tr.progOut, tr.progErr, tr.progRuntime, tr.progMemory, tr.compOut, tr.compErr, tr.compRuntime, tr.compMemory))
        .update(Queued, Some(0), None, None, None, None, None, None, None, None, None)
    } yield ()
  }
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
  def byProblemOption(id: Int, preferredLang: Lang)(implicit db: Database, ec: ExecutionContext): Future[Option[ProblemTranslation]] = {
    // cannot be inlined because type cannot be inferred
    val query = for {
      p <- ProblemTranslations if p.problemId === id
    } yield (p.problemId, p.lang, p.title, p.description)
    db.run(query.result).map { pt =>
      // TODO: ordering would need to work on Rep[Lang] in order to sort in the database
      pt.sortBy(_._2 /*lang*/)(LanguageHelper.ordering(preferredLang))
        .map(ProblemTranslation.tupled(_))
    }.flatMap { pt => Future.successful(pt.headOption) }
  }

  def byProblemLang(problemId: Int, lang: Lang)(implicit db: Database, ec: ExecutionContext): Future[Option[ProblemTranslation]] =
    db.run(this.filter(t => t.problemId === problemId && t.lang === lang).result.headOption)

  def problemTitleListByLang(preferredLang: Lang)(implicit db: Database, ec: ExecutionContext): Future[Map[Int, String]] = {
    db.run((for {
      p <- ProblemTranslations
    } yield (p.problemId, p.lang, p.title, p.description)).result).map { list =>
      list.map(ProblemTranslation.tupled(_)).groupBy(_.problemId).map {
        case (p: Int, l: Seq[ProblemTranslation]) =>
          p -> l.sortBy(_.language)(LanguageHelper.ordering(preferredLang)).headOption.fold("")(_.title)
      }
    }
  }

  def update(lang: String, problemTranslation: ProblemTranslation)(implicit db: Database): Future[Int] =
    db.run(this.filter(t => t.problemId === problemTranslation.problemId && t.lang === Lang(lang)).update(problemTranslation))
}
