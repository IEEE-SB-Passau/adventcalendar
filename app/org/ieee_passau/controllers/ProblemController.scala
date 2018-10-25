package org.ieee_passau.controllers

import org.ieee_passau.controllers.Beans.UpdateRankingM
import org.ieee_passau.forms.ProblemForms
import org.ieee_passau.models._
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
import play.api.libs.concurrent.Akka
import play.api.mvc._

object ProblemController extends Controller with PermissionCheck {

  def index: Action[AnyContent] = requirePermission(Moderator) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.problem.index(Problems.sortBy(_.door.asc).list))
  }}

  def edit(id: Int): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Problems.byId(id).firstOption.map { problem =>
      Ok(org.ieee_passau.views.html.problem.edit(id,
        Testcases.filter(_.problemId === id).sortBy(_.position.asc).list,
        EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).list,
        ProblemTranslations.filter(_.problemId === id).sortBy(_.lang.asc).list,
        ProblemForms.problemForm.fill(problem), EvalModes.list))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def delete(id: Int): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Problems.filter(_.id === id).delete
    Akka.system.actorSelection("user/RankingActor") ! UpdateRankingM
    Redirect(org.ieee_passau.controllers.routes.ProblemController.index())
  }}

  def insert: Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.problem.insert(ProblemForms.problemForm, EvalModes.list))
  }}

  def save: Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    ProblemForms.problemForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.problem.insert(errorForm, EvalModes.list))
      },

      newProblem => {
        Problems += newProblem
        val id = Problems.byDoor(newProblem.door).firstOption.get.id.get
        Akka.system.actorSelection("user/RankingActor") ! UpdateRankingM
        Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(id))
          .flashing("success" -> Messages("problem.create.message", newProblem.title))
      }
    )
  }}

  def update(id: Int): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    ProblemForms.problemForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.problem.edit(id,
          Testcases.filter(_.problemId === id).sortBy(_.position.asc).list,
          EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).list,
          ProblemTranslations.filter(_.problemId === id).sortBy(_.lang.asc).list,
          errorForm, EvalModes.list))
      },

      problem => {
        Problems.update(id, problem)
        Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(id))
          .flashing("success" ->  Messages("problem.update.message", problem.title))
      }
    )
  }}

  def addTranslation(problemId: Int): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.problemTranslation.insert(problemId, ProblemForms.problemTranslationForm))
  }}

  def saveTranslation(problemId: Int): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    ProblemForms.problemTranslationForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.problemTranslation.insert(problemId, errorForm))
      },

      newTrans => {
        if (ProblemTranslations.byProblemLang(problemId, newTrans.language).firstOption.isDefined) {
          BadRequest(org.ieee_passau.views.html.problemTranslation.insert(problemId,
            ProblemForms.problemTranslationForm.fill(newTrans).withError("duplicate_translation", Messages("problem.translation.create.error.exists"))))
        } else {
          ProblemTranslations += newTrans
          Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(newTrans.problemId))
            .flashing("success" -> Messages("problem.translation.create.message", newTrans.title, newTrans.language.code))
        }
      }
    )
  }}

  def editTranslation(problemId: Int, lang: String): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    ProblemTranslations.byProblemLang(problemId, lang).firstOption.map { trans =>
      Ok(org.ieee_passau.views.html.problemTranslation.edit(problemId, ProblemForms.problemTranslationForm.fill(trans)))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}


  def updateTranslation(problemId: Int, lang: String): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    ProblemForms.problemTranslationForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.problemTranslation.edit(problemId, errorForm))
      },

      trans => {
        ProblemTranslations.update(lang, trans)
        Redirect(org.ieee_passau.controllers.routes.ProblemController.editTranslation(problemId, lang))
          .flashing("success" ->  Messages("problem.translation.update.message", trans.title, trans.language.code))
      }
    )
  }}

  def deleteTranslation(problemId: Int, lang: String): Action[AnyContent] = requirePermission(Admin) { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    ProblemTranslations.byProblemLang(problemId, lang).delete
    Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(problemId))
  }}
}
