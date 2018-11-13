package org.ieee_passau.controllers

import java.util.Date

import com.google.inject.Inject
import org.ieee_passau.models._
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional, _}
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class TestcaseController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                   val components: MessagesControllerComponents,
                                   implicit val ec: ExecutionContext,
                                   val config: Configuration
                                  ) extends MasterController(dbConfigProvider, components, ec, config) {

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
    db.run(Testcases.filter(_.id === id).delete).map { _ =>
      Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(pid))
    }
  }}

  def insert(pid: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Visibilities.to[List].result).map { visibilities =>
      Ok(org.ieee_passau.views.html.testcase.insert(pid, visibilities, testcaseForm))
    }
  }}

  def save(pid: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    testcaseForm.bindFromRequest.fold(
      errorForm => {
        db.run(Visibilities.to[List].result).map { visibilities =>
          BadRequest(org.ieee_passau.views.html.testcase.insert(pid, visibilities, errorForm))
        }
      },

      newTestcase => {
        val now = new Date()
        val solutionsQuery = for {
          s <- Solutions if s.problemId === pid
        } yield s.id

        db.run((Testcases returning Testcases.map(_.id)) += newTestcase).zip(db.run(solutionsQuery.result)).map {
          case (testcase, testruns) =>
            Problems.updatePoints(pid)
            testruns.map(s =>
              db.run(Testruns += Testrun(None, s, testcase, None, None, None, None, None, None, None, None, None, None, Queued, None, now, Some(0), None, None, now))
            )
            Redirect(org.ieee_passau.controllers.routes.TestcaseController.edit(pid, testcase))
              .flashing("success" -> rs.messages("testcase.create.message", newTestcase.position))
        }
      }
    )
  }}

  def update(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    testcaseForm.bindFromRequest.fold(
      errorForm => {
        db.run(Visibilities.to[List].result).map { visibilities =>
          BadRequest(org.ieee_passau.views.html.testcase.edit(pid, id, visibilities, errorForm))
        }
      },

      testcase => {
        val now = new Date()
        Testcases.update(id, testcase).foreach(_ => Problems.updatePoints(pid))
        val solutionsQuery = for {
          s <- Solutions if s.problemId === pid
        } yield s.id
        db.run(solutionsQuery.result).map { solutions =>
          solutions.foreach(s =>
            db.run(Testruns.bySolutionIdTestcaseId(s, id).result.headOption).foreach {
              case Some(existing) =>
                Testruns.update(id, existing.copy(result = Queued, stage = Some(0)))
              case _ =>
                db.run(Testruns += Testrun(None, s, id, None, None, None, None, None, None, None, None, None, None, Queued, None, now, Some(0), None, None, now))
            }
          )

          Redirect(org.ieee_passau.controllers.routes.TestcaseController.edit(pid, id))
            .flashing("success" -> rs.messages("testcase.update.message", testcase.position))
        }
      }
    )
  }}

  val testcaseForm = Form(
    mapping(
      "id" -> optional(number),
      "problemId" -> number,
      "position" -> number, // TODO check uniqueness for problem
      "visibility" -> text,
      "input" -> text,
      "output" -> text,
      "points" -> number
    )((id: Option[Int], problemId: Int, position: Int, visibility: String, input: String, output: String, points: Int)
    => Testcase(id, problemId, position, Visibility(visibility), input, output, points))
    ((t: Testcase) => Some(t.id, t.problemId, t.position, t.visibility.scope, t.input, t.expectedOutput, t.points))
  )
}
