package org.ieee_passau.controllers

import com.google.inject.Inject
import org.ieee_passau.models._
import org.ieee_passau.utils.DbHelper
import play.api.{Configuration, Environment}
import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional, _}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.io.File

class EvalTaskController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                   val components: MessagesControllerComponents,
                                   implicit val ec: ExecutionContext,
                                   val config: Configuration,
                                   val env: Environment
                                  ) extends MasterController(dbConfigProvider, components, ec, config, env) {

  def edit(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(EvalTasks.byId(id).result.headOption).map {
      case Some(task) => Ok(org.ieee_passau.views.html.evaltask.edit(pid, id, evalTaskForm.fill(task)))
      case _ => NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def delete(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    DbHelper.retry(for {
      _ <- EvalTasks.filter(_.id === id).delete
      _ <- Problems.reeval(pid)
    } yield ()).map(_ =>
      Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(pid))
    )
  }}

  def insert(pid: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    Future.successful(Ok(org.ieee_passau.views.html.evaltask.insert(pid, evalTaskForm)))
  }}

  def save(pid: Int): Action[MultipartFormData[TemporaryFile]] = requirePermission(Admin, parse.multipartFormData) { implicit admin =>
    Action.async(parse.multipartFormData) { implicit rs =>
      evalTaskForm.bindFromRequest.fold(
        errorForm => {
          Future.successful(BadRequest(org.ieee_passau.views.html.evaltask.insert(pid, errorForm)))
        },

        newTaskRaw => {
          rs.body.file("program").map { program =>
            val newTask: EvalTask = newTaskRaw.copy(filename = program.filename, file = new File(program.ref.path.toFile).toByteArray())
            DbHelper.retry(for {
              newId <- (EvalTasks returning EvalTasks.map(_.id)) += newTask
              _ <- Problems.reeval(pid)
            } yield newId).map(newTaskId =>
              Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, newTaskId))
                .flashing("success" -> rs.messages("evaltask.create.message", newTaskRaw.position))
            )
          } getOrElse {
            Future.successful(BadRequest(org.ieee_passau.views.html.evaltask.insert(pid,
              evalTaskForm.fill(newTaskRaw).withError("program", rs.messages("evaltask.create.error.filemissing")))))
          }
        }
      )
    }
  }

  def update(pid: Int, id: Int): Action[MultipartFormData[TemporaryFile]] = requirePermission(Admin, parse.multipartFormData) { implicit admin =>
    Action.async(parse.multipartFormData) { implicit rs =>
      evalTaskForm.bindFromRequest.fold(
        errorForm => {
          Future.successful(BadRequest(org.ieee_passau.views.html.evaltask.edit(pid, id, errorForm)))
        },

        task => {
          rs.body.file("program").map { program =>
            val newTask = task.copy(filename = program.filename, file = new File(program.ref.path.toFile).toByteArray())
            DbHelper.retry(for {
              _ <- EvalTasks.update(id, newTask)
              _ <- Problems.reeval(pid)
            } yield ()).map(_ =>
              Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id))
                .flashing("success" -> rs.messages("evaltask.update.message", task.position))
            )
          } getOrElse {
            // update options only, leave program intact
            DbHelper.retry(for {
              ot <- EvalTasks.byId(id).result.head
              nt <- DBIO.successful(task.copy(filename = ot.filename, file = ot.file))
              _ <- EvalTasks.update(id, nt)
              _ <- Problems.reeval(pid)
            } yield ()).map(_ =>
              Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id))
                .flashing("success" -> rs.messages("evaltask.update.message", task.position))
            )
          }
        }
      )
    }
  }

  val evalTaskForm: Form[EvalTask] = Form(
    mapping(
      "id" -> optional(number),
      "problemId" -> number,
      "position" -> number, // TODO check uniqueness for problem
      "command" -> text,
      "outputCheck" -> boolean,
      "scoreCalc" -> boolean,
      "runCorrect" -> boolean,
      "runWrong" -> boolean
    )
    ((id: Option[Int], problemId: Int, position: Int, command: String, outputCheck: Boolean, scoreCalc: Boolean,
      runCorrect: Boolean, runWrong: Boolean) =>
      EvalTask(id, problemId, position, command, "", Array(), outputCheck, scoreCalc, command.contains("{stdIn}"),
        command.contains("{expOut}"), command.contains("{progOut}"), command.contains("{program}"), runCorrect, runWrong))
    ((t: EvalTask) => Some(t.id, t.problemId, t.position, t.command, t.outputCheck, t.scoreCalc, t.runCorrect, t.runWrong))
  )
}
