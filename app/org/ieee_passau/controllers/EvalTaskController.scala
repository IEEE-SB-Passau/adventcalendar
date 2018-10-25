package org.ieee_passau.controllers

import org.ieee_passau.forms.TestcaseForms
import org.ieee_passau.models.{Admin, EvalTask, EvalTasks}
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.mvc._

import scala.reflect.io.File

object EvalTaskController extends Controller with PermissionCheck {

  def edit(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => DBAction { implicit rs =>
    EvalTasks.byId(id).firstOption.map { task =>
      Ok(org.ieee_passau.views.html.evaltask.edit(pid, id, TestcaseForms.evalTaskForm.fill(task)))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def delete(pid: Int, id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => DBAction { implicit rs =>
    EvalTasks.filter(_.id === id).delete
    Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(pid))
  }}

  def insert(pid: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => DBAction { implicit rs =>
    Ok(org.ieee_passau.views.html.evaltask.insert(pid, TestcaseForms.evalTaskForm))
  }}

  def save(pid: Int): Action[MultipartFormData[TemporaryFile]] = requirePermission(Admin, parse.multipartFormData) { implicit admin =>
    DBAction(parse.multipartFormData) { implicit rs =>
      TestcaseForms.evalTaskForm.bindFromRequest.fold(
        errorForm => {
          BadRequest(org.ieee_passau.views.html.evaltask.insert(pid, errorForm))
        },

        newTaskRaw => {
          rs.body.file("program").map { program =>
            val newTask: EvalTask = newTaskRaw.copy(filename = program.filename, file = new File(program.ref.file).toByteArray())
            val id = (EvalTasks returning EvalTasks.map(_.id)) += newTask
            Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id))
              .flashing("success" -> Messages("evaltask.create.message", newTaskRaw.position))
          } getOrElse {
            BadRequest(org.ieee_passau.views.html.evaltask.insert(pid,
              TestcaseForms.evalTaskForm.fill(newTaskRaw).withError("program", Messages("evaltask.create.error.filemissing"))))
          }
        }
      )
    }
  }

  def update(pid: Int, id: Int): Action[MultipartFormData[TemporaryFile]] = requirePermission(Admin, parse.multipartFormData) { implicit admin =>
    DBAction(parse.multipartFormData) { implicit rs =>
      TestcaseForms.evalTaskForm.bindFromRequest.fold(
        errorForm => {
          BadRequest(org.ieee_passau.views.html.evaltask.edit(pid, id, errorForm))
        },

        task => {
          rs.body.file("program").map { program =>
            val newTask = task.copy(filename = program.filename, file = new File(program.ref.file).toByteArray())
            EvalTasks.update(id, newTask)
            Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id))
              .flashing("success" -> Messages("evaltask.update.message", task.position))
          } getOrElse {
            val oldTask = EvalTasks.byId(id).first
            val newTask = task.copy(filename = oldTask.filename, file = oldTask.file)
            EvalTasks.update(id, newTask)
            Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id))
              .flashing("success" -> Messages("evaltask.update.message", task.position))
          }
        }
      )
    }
  }
}
