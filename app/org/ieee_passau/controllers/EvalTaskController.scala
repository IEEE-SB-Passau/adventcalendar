package org.ieee_passau.controllers

import com.google.inject.Inject
import org.ieee_passau.models.{Admin, EvalTask, EvalTasks}
import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional, _}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.io.File

class EvalTaskController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                   val components: MessagesControllerComponents
                                  ) extends MasterController(dbConfigProvider, components) {

  def edit(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(EvalTasks.byId(id).result.headOption).map {
      case Some(task) => Ok(org.ieee_passau.views.html.evaltask.edit(pid, id, evalTaskForm.fill(task)))
      case _ => NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def delete(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(EvalTasks.filter(_.id === id).delete).map { _ =>
      Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(pid))
    }
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
            db.run((EvalTasks returning EvalTasks.map(_.id)) += newTask).map { newTaskId =>
              Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, newTaskId)).flashing("success" -> rs.messages("evaltask.create.message", newTaskRaw.position))
            }
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
            EvalTasks.update(id, newTask)
            Future.successful(Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id)).flashing("success" -> rs.messages("evaltask.update.message", task.position)))
          } getOrElse {
            db.run(EvalTasks.byId(id).result.head).map { ot =>
              val newTask = task.copy(filename = ot.filename, file = ot.file)
              EvalTasks.update(id, newTask)
            }.map { _ =>
              Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id))
                .flashing("success" -> rs.messages("evaltask.update.message", task.position))
            }
          }
        }
      )
    }
  }

  val evalTaskForm = Form(
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
