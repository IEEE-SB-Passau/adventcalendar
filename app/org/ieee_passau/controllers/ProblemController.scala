package org.ieee_passau.controllers

import org.ieee_passau.controllers.Beans.UpdateRankingM
import org.ieee_passau.forms.ProblemForms
import org.ieee_passau.models.{EvalModes, EvalTasks, Problems, Testcases}
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
import play.api.libs.concurrent.Akka
import play.api.mvc._

object ProblemController extends Controller with PermissionCheck {

  def index: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.problem.index(Problems.sortBy(_.door.asc).list))
  }}

  def edit(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Problems.byId(id).firstOption.map { problem =>
      Ok(org.ieee_passau.views.html.problem.edit(id,
        Testcases.filter(_.problemId === id).sortBy(_.position.asc).list,
        EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).list,
        ProblemForms.problemForm.fill(problem), EvalModes.list))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def delete(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Problems.filter(_.id === id).delete
    Akka.system.actorSelection("user/RankingActor") ! UpdateRankingM
    Redirect(org.ieee_passau.controllers.routes.ProblemController.index())
  }}

  def insert: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.problem.insert(ProblemForms.problemForm, EvalModes.list))
  }}

  def save: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
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

  def update(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    ProblemForms.problemForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.problem.edit(id,
          Testcases.filter(_.problemId === id).sortBy(_.position.asc).list,
          EvalTasks.filter(_.problemId === id).sortBy(_.position.asc).list,
          errorForm, EvalModes.list))
      },

      problem => {
        Problems.update(id, problem)
        Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(id))
          .flashing("success" ->  Messages("problem.update.message", problem.title))
      }
    )
  }}
}
