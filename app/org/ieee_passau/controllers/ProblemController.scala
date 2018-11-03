package org.ieee_passau.controllers

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Inject
import com.google.inject.name.Named
import org.ieee_passau.controllers.Beans.UpdateRankingM
import org.ieee_passau.models.{EvalMode, Problem, ProblemTranslation, Problems, _}
import org.ieee_passau.utils.{AkkaHelper, FutureHelper, LanguageHelper}
import play.api.Configuration
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
                                  val system: ActorSystem,
                                  implicit val config: Configuration,
                                  @Named(AkkaHelper.rankingActor) val rankingActor: ActorRef
                                 ) extends MasterController(dbConfigProvider, components, ec) {

  def index: Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    db.run(Problems.sortBy(_.door.asc).result).map(problems =>
      Ok(org.ieee_passau.views.html.problem.index(problems.toList)))
  }}

  def edit(id: Int): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    implicit val LangTypeMapper: JdbcType[Lang] with BaseTypedType[Lang] = LanguageHelper.LangTypeMapper

    db.run(Problems.byId(id).result.headOption).flatMap {
      case Some(problem) =>
        val testCaseQuery = db.run(Testcases.filter(_.problemId === id).sortBy(_.position.asc).result)
        val evalTaskQuery = db.run(EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).result)
        val problemTranslationQuery = db.run(ProblemTranslations.filter(_.problemId === id).sortBy(_.lang.asc).result)
        val evalModesQuery = db.run(EvalModes.result)

        testCaseQuery.flatMap(testCases =>
          evalTaskQuery.flatMap(evalTasks =>
            problemTranslationQuery.flatMap(problemTranslations =>
              evalModesQuery.map(evalModes =>
                Ok(org.ieee_passau.views.html.problem.edit(id, testCases.toList, evalTasks.toList,
                  problemTranslations.toList, problemForm.fill(problem), evalModes.toList))
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
    db.run(EvalModes.result).map(evalModes =>
      Ok(org.ieee_passau.views.html.problem.insert(problemForm, evalModes.toList))
    )
  }}

  def save: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    problemForm.bindFromRequest.fold(
      errorForm => {
        db.run(EvalModes.result).map(evalModes =>
          Ok(org.ieee_passau.views.html.problem.insert(errorForm, evalModes.toList))
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
    implicit val LangTypeMapper: JdbcType[Lang] with BaseTypedType[Lang] = LanguageHelper.LangTypeMapper

    problemForm.bindFromRequest.fold(
      errorForm => {
        val testCaseQuery = db.run(Testcases.filter(_.problemId === id).sortBy(_.position.asc).result)
        val evalTaskQuery = db.run(EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).result)
        val problemTranslationQuery = db.run(ProblemTranslations.filter(_.problemId === id).sortBy(_.lang.asc).result)
        val evalModesQuery = db.run(EvalModes.result)

        testCaseQuery.flatMap(testCases =>
          evalTaskQuery.flatMap(evalTasks =>
            problemTranslationQuery.flatMap(problemTranslations =>
              evalModesQuery.map(evalModes =>
                Ok(org.ieee_passau.views.html.problem.edit(id, testCases.toList, evalTasks.toList,
                  problemTranslations.toList, errorForm, evalModes.toList))
              )
            )
          )
        )
      },

      problem => {
        db.run(Problems.update(id, problem)).map(_ =>
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
        db.run(ProblemTranslations.byProblemLang(problemId, newTrans.language).result.headOption).flatMap {
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
    db.run(ProblemTranslations.byProblemLang(problemId, lang).result.headOption).map {
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
        db.run(ProblemTranslations.update(lang, trans)).map(_ =>
          Redirect(org.ieee_passau.controllers.routes.ProblemController.editTranslation(problemId, lang))
            .flashing("success" -> rs.messages("problem.translation.update.message", trans.title, trans.language.code))
        )
      }
    )
  }}

  def deleteTranslation(problemId: Int, lang: String): Action[AnyContent] = requirePermission(Moderator) { implicit admin =>Action.async { implicit rs =>
    db.run(ProblemTranslations.byProblemLang(problemId, lang).delete).map(_ =>
      Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(problemId))
    )
  }}

  val problemForm = Form(
    mapping(
      "id" -> optional(text),
      "title" -> nonEmptyText,
      "door" -> number, //(1, 24)
      "readableStart" -> date("yyyy-MM-dd HH:mm"),
      "readableStop" -> date("yyyy-MM-dd HH:mm"),
      "solvableStart" -> date("yyyy-MM-dd HH:mm"),
      "solvableStop" -> date("yyyy-MM-dd HH:mm"),
      "evalMode" -> nonEmptyText,
      "cpuFactor" -> of[Float],
      "memFactor" -> of[Float]
    )
    ((id: Option[String], title, door, readableStart, readableStop, solvableStart, solvableStop,
      evalMode: String, cpuFacotr: Float, memFator: Float) =>
      Problem(if (id.isDefined) Some(id.get.toInt) else None, title, door, "", readableStart, readableStop,
        solvableStart, solvableStop, EvalMode(evalMode), cpuFacotr, memFator))
    ((p: Problem) => Some(Some(p.id.toString), p.title, p.door, p.readableStart, p.readableStop,
      p.solvableStart, p.solvableStop, p.evalMode.mode, p.cpuFactor, p.memFactor))

      verifying("viserror.date.reverse", p => p.readableStart.compareTo(p.readableStop) < 0)
      verifying("sloverror.date.reverse", p => p.solvableStart.compareTo(p.solvableStop) < 0)
      verifying("problem.create.error.door",
      p => Await.result(p.id match {
        case Some(id) => Problems.doorAvailable(p.door, id)
        case None => Problems.doorAvailable(p.door)
      }, FutureHelper.dbTimeout)
    )
  )

  val problemTranslationForm = Form(
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
