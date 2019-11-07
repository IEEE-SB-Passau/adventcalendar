package org.ieee_passau.controllers

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Inject
import com.google.inject.name.Named
import org.ieee_passau.controllers.Beans.UpdateRankingM
import org.ieee_passau.models.{EvalMode, Problem, ProblemTranslation, Problems, _}
import org.ieee_passau.utils.{AkkaHelper, FutureHelper, LanguageHelper}
import org.ieee_passau.utils.LanguageHelper.LangTypeMapper
import play.api.{Configuration, Environment}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.Lang
import play.api.mvc._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, ExecutionContext, Future}

class ProblemController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                  val components: MessagesControllerComponents,
                                  implicit val ec: ExecutionContext,
                                  implicit val config: Configuration,
                                  val env: Environment,
                                  val system: ActorSystem,
                                  @Named(AkkaHelper.rankingActor) val rankingActor: ActorRef
                                 ) extends MasterController(dbConfigProvider, components, ec, config, env) {

  def index: Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    ProblemTranslations.problemTitleListByLang(admin.get.lang).flatMap { transList =>
      db.run(Problems.sortBy(_.door.asc).to[List].result).map { problems =>
        Ok(org.ieee_passau.views.html.problem.index(problems, transList))
      }
    }
  }}

  def edit(id: Int): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    implicit val LangTypeMapper: JdbcType[Lang] with BaseTypedType[Lang] = LanguageHelper.LangTypeMapper

    db.run(Problems.byId(id).result.headOption).flatMap {
      case Some(problem) =>
        val testCaseQuery = db.run(Testcases.filter(_.problemId === id).sortBy(_.position.asc).to[List].result)
        val evalTaskQuery = db.run(EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).to[List].result)
        val problemTranslationQuery = db.run(ProblemTranslations.filter(_.problemId === id).sortBy(_.lang.asc).to[List].result)
        val evalModesQuery = db.run(EvalModes.to[List].result)

        testCaseQuery.flatMap(testCases =>
          evalTaskQuery.flatMap(evalTasks =>
            problemTranslationQuery.flatMap(problemTranslations =>
              evalModesQuery.map(evalModes =>
                Ok(org.ieee_passau.views.html.problem.edit(id, testCases, evalTasks,
                  problemTranslations, problemForm.fill(problem), evalModes))
              )
            )
          )
        )
      case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }}

  def delete(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Problems.filter(_.id === id).delete).map {_ =>
      rankingActor ! UpdateRankingM
      Redirect(org.ieee_passau.controllers.routes.ProblemController.index())
    }
  }}

  def insert: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(EvalModes.to[List].result).map(evalModes =>
      Ok(org.ieee_passau.views.html.problem.insert(problemForm, evalModes))
    )
  }}

  def save: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    problemForm.bindFromRequest.fold(
      errorForm => {
        db.run(EvalModes.to[List].result).map(evalModes =>
          Ok(org.ieee_passau.views.html.problem.insert(errorForm, evalModes))
        )
      },

      newProblem => {
        db.run((Problems returning Problems.map(_.id)) += newProblem).map { id =>
          rankingActor ! UpdateRankingM
          Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(id))
            .flashing("success" -> rs.messages("problem.create.message", newProblem.title))
        }
      }
    )
  }}

  def update(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    problemForm.bindFromRequest.fold(
      errorForm => {
        val testCaseQuery = db.run(Testcases.filter(_.problemId === id).sortBy(_.position.asc).to[List].result)
        val evalTaskQuery = db.run(EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).to[List].result)
        val problemTranslationQuery = db.run(ProblemTranslations.filter(_.problemId === id).sortBy(_.lang.asc).to[List].result)
        val evalModesQuery = db.run(EvalModes.to[List].result)

        testCaseQuery.flatMap(testCases =>
          evalTaskQuery.flatMap(evalTasks =>
            problemTranslationQuery.flatMap(problemTranslations =>
              evalModesQuery.map(evalModes =>
                Ok(org.ieee_passau.views.html.problem.edit(id, testCases, evalTasks,
                  problemTranslations, errorForm, evalModes))
              )
            )
          )
        )
      },

      problem => {
        Problems.update(id, problem).map(_ =>
          Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(id))
            .flashing("success" -> rs.messages("problem.update.message", problem.title))
        )
      }
    )
  }}

  def addTranslation(problemId: Int): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action { implicit rs =>
    Ok(org.ieee_passau.views.html.problemTranslation.insert(problemId, problemTranslationForm))
  }}

  def saveTranslation(problemId: Int): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    problemTranslationForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.problemTranslation.insert(problemId, errorForm)))
      },

      newTrans => {
        ProblemTranslations.byProblemLang(problemId, newTrans.language).flatMap {
          case Some(_) =>
            Future.successful(BadRequest(org.ieee_passau.views.html.problemTranslation.insert(problemId,
              problemTranslationForm.fill(newTrans).withError("duplicate_translation", rs.messages("problem.translation.create.error.exists")))))
          case _ =>
            db.run(ProblemTranslations += newTrans).map(_ =>
              Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(newTrans.problemId))
                .flashing("success" -> rs.messages("problem.translation.create.message", newTrans.title, newTrans.language.code))
            )
        }
      }
    )
  }}

  def editTranslation(problemId: Int, lang: String): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    ProblemTranslations.byProblemLang(problemId, Lang(lang)).map {
      case Some(trans) =>
        Ok(org.ieee_passau.views.html.problemTranslation.edit(problemId, problemTranslationForm.fill(trans)))
      case _ =>
        NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def updateTranslation(problemId: Int, lang: String): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    problemTranslationForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.problemTranslation.edit(problemId, errorForm)))
      },

      trans => {
        ProblemTranslations.update(lang, trans).map(_ =>
          Redirect(org.ieee_passau.controllers.routes.ProblemController.editTranslation(problemId, lang))
            .flashing("success" -> rs.messages("problem.translation.update.message", trans.title, trans.language.code))
        )
      }
    )
  }}

  def deleteTranslation(problemId: Int, lang: String): Action[AnyContent] = requirePermission(Moderator) { implicit admin =>Action.async { implicit rs =>
    db.run(ProblemTranslations.filter(t => t.problemId === problemId && t.lang === Lang(lang)).delete).map(_ =>
      Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(problemId))
    )
  }}

  val problemForm: Form[Problem] = Form(
    mapping(
      "id" -> optional(text),
      "door" -> number, //(1, 24)
      "readableStart" -> date("yyyy-MM-dd HH:mm"),
      "readableStop" -> date("yyyy-MM-dd HH:mm"),
      "solvableStart" -> date("yyyy-MM-dd HH:mm"),
      "solvableStop" -> date("yyyy-MM-dd HH:mm"),
      "evalMode" -> nonEmptyText,
      "cpuFactor" -> of[Float],
      "memFactor" -> of[Float]
    )
    ((id: Option[String], door, readableStart, readableStop, solvableStart, solvableStop,
      evalMode: String, cpuFactor: Float, memFactor: Float) =>
      Problem(if (id.isDefined) Some(id.get.toInt) else None, "", door, "", readableStart, readableStop,
        solvableStart, solvableStop, EvalMode(evalMode), cpuFactor, memFactor, 0))
    ((p: Problem) => Some(Some(p.id.toString), p.door, p.readableStart, p.readableStop,
      p.solvableStart, p.solvableStop, p.evalMode.mode, p.cpuFactor, p.memFactor))

      verifying("problem.create.error.datereverse.vis", p => p.readableStart.compareTo(p.readableStop) < 0)
      verifying("problem.create.error.datereverse.solve", p => p.solvableStart.compareTo(p.solvableStop) < 0)
      verifying("problem.create.error.door",
      p => Await.result(p.id match {
        case Some(id) => Problems.doorAvailable(p.door, id)
        case None => Problems.doorAvailable(p.door)
      }, FutureHelper.dbTimeout)
    )
  )

  val problemTranslationForm: Form[ProblemTranslation] = Form(
    mapping(
      "problemId" -> number,
      "language" -> nonEmptyText,
      "title" -> nonEmptyText,
      "description" -> text
    )((problemId: Int, language: String, title: String, description: String)
    => ProblemTranslation(problemId, Lang(language), title, description))
    ((p: ProblemTranslation) => Some(p.problemId, p.language.code, p.title, p.description))
  )
}
