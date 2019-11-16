package org.ieee_passau.controllers

import java.util.Date

import com.google.inject.Inject
import org.ieee_passau.models._
import org.ieee_passau.utils.DbHelper
import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional, _}
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import play.api.{Configuration, Environment}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class TestcaseController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                   val components: MessagesControllerComponents,
                                   implicit val ec: ExecutionContext,
                                   val config: Configuration,
                                   val env: Environment
                                  ) extends MasterController(dbConfigProvider, components, ec, config, env) {

  def edit(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Testcases.byId(id).result.headOption).flatMap {
      case Some(testcase) =>
        db.run(Visibilities.to[List].result).map { visibilities =>
          Ok(org.ieee_passau.views.html.testcase.edit(pid, id, visibilities, testcaseForm.fill(testcase)))
        }
      case None => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }}

  def delete(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    DbHelper.retry(for {
      _ <- Testcases.filter(_.id === id).delete
      _ <- Testruns.filter(_.testcaseId === id).delete
    } yield ()).map(_ =>
      Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(pid))
    )
  }}

  def insert(pid: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Visibilities.to[List].result).map { visibilities =>
      Ok(org.ieee_passau.views.html.testcase.insert(pid, visibilities, testcaseForm))
    }
  }}

  def save(pid: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    var filledForm = testcaseForm.bindFromRequest()
    if (!Testcases.isPositionAvailable(pid, filledForm.get.position)) {
      filledForm = filledForm.withError("position", "testcase.position.error.taken")
    }
    filledForm.fold(
      errorForm => {
        db.run(Visibilities.to[List].result).map { visibilities =>
          BadRequest(org.ieee_passau.views.html.testcase.insert(pid, visibilities, errorForm))
        }
      },

      newTestcase => {
        val now = new Date()
        DbHelper.retry(for {
          newId <- (Testcases returning Testcases.map(_.id)) += newTestcase
          solutions <- Solutions.filter(_.problemId === pid).map(_.id).result
          _ <- DBIO.sequence(solutions.map(s =>
            Testruns += Testrun(None, s, newId, None, None, None, None, None, None, None, None, None, None, Queued, None, now, Some(0), None, None, now)
          ))
        } yield newId).map(testcase =>
          Redirect(org.ieee_passau.controllers.routes.TestcaseController.edit(pid, testcase))
            .flashing("success" -> rs.messages("testcase.create.message", newTestcase.position))
        )
      }
    )
  }}

  def update(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    var filledForm = testcaseForm.bindFromRequest()
    if (!Testcases.isPositionAvailable(id, pid, filledForm.get.position)) {
      filledForm = filledForm.withError("position", "testcase.position.error.taken")
    }
    filledForm.fold(
      errorForm => {
        db.run(Visibilities.to[List].result).map { visibilities =>
          BadRequest(org.ieee_passau.views.html.testcase.edit(pid, id, visibilities, errorForm))
        }
      },

      testcase => {
        DbHelper.retry(for {
          _ <- Testcases.update(id, testcase)
          solutions <- Solutions.filter(_.problemId === pid).map(_.id).result
          _ <- DBIO.sequence(solutions.map(s =>
            Testruns.filter(r => r.testcaseId === id && r.solutionId === s).map(r => (r.result, r.stage)).update((Queued, Some(0)))
          ))
        } yield ()).map(_ =>
          Redirect(org.ieee_passau.controllers.routes.TestcaseController.edit(pid, id))
            .flashing("success" -> rs.messages("testcase.update.message", testcase.position))
        )
      }
    )
  }}

  val testcaseForm: Form[Testcase] = Form(
    mapping(
      "id" -> optional(number),
      "problemId" -> number,
      "position" -> number,
      "visibility" -> text,
      "input" -> text,
      "output" -> text,
      "points" -> number
    )((id: Option[Int], problemId: Int, position: Int, visibility: String, input: String, output: String, points: Int)
    => Testcase(id, problemId, position, Visibility(visibility), input, output, points))
    ((t: Testcase) => Some(t.id, t.problemId, t.position, t.visibility.scope, t.input, t.expectedOutput, t.points))
  )
}
