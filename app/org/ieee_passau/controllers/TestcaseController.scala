package org.ieee_passau.controllers

import java.util.Date

import org.ieee_passau.forms.TestcaseForms
import org.ieee_passau.models._
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
import play.api.mvc._

object TestcaseController extends Controller with PermissionCheck {

  def edit(pid: Int, id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Testcases.byId(id).firstOption.map { testcase =>
      val visibilities = Visibilities.list
      Ok(org.ieee_passau.views.html.testcase.edit(pid, id, visibilities, TestcaseForms.testcaseForm.fill(testcase)))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def delete(pid: Int, id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Testcases.filter(_.id === id).delete
    Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(pid))
  }}

  def insert(pid: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    val visibilities = Visibilities.list
    Ok(org.ieee_passau.views.html.testcase.insert(pid, visibilities, TestcaseForms.testcaseForm))
  }}

  def save(pid: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    TestcaseForms.testcaseForm.bindFromRequest.fold(
      errorForm => {
        val visibilities = Visibilities.list
        BadRequest(org.ieee_passau.views.html.testcase.insert(pid, visibilities, errorForm))
      },

      newTestcase => {
        val id = (Testcases returning Testcases.map(_.id)) += newTestcase
        val now = new Date()
        val solutions = for {
          s <- Solutions if s.problemId === pid
        } yield s.id
        solutions.foreach(s =>
          Testruns += Testrun(None, s, id, None, None, None, None, None, None, None, None, None, None, Queued, None, now, Some(0), None, None, now)
        )
        Redirect(org.ieee_passau.controllers.routes.TestcaseController.edit(pid, id))
          .flashing("success" -> Messages("testcase.create.message", newTestcase.position))
      }
    )
  }}

  def update(pid: Int, id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    TestcaseForms.testcaseForm.bindFromRequest.fold(
      errorForm => {
        val visibilities = Visibilities.list
        BadRequest(org.ieee_passau.views.html.testcase.edit(pid, id, visibilities, errorForm))
      },

      testcase => {
        Testcases.update(id, testcase)
        val now = new Date()
        val solutions = for {
          s <- Solutions if s.problemId === pid
        } yield s.id
        solutions.foreach(s => {
          val maybeExisting = Testruns.bySolutionIdTestcaseId(s, id).firstOption
          if (maybeExisting.isDefined) {
            Testruns.update(maybeExisting.get.id.get, maybeExisting.get.copy(result = Queued, stage = Some(0)))
          } else {
            Testruns += Testrun(None, s, id, None, None, None, None, None, None, None, None, None, None, Queued, None, now, Some(0), None, None, now)
          }
        })
        Redirect(org.ieee_passau.controllers.routes.TestcaseController.edit(pid, id))
          .flashing("success" -> Messages("testcase.update.message", testcase.position))
      }
    )
  }}
}
