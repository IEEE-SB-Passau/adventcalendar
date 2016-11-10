package org.ieee_passau.controllers

import org.ieee_passau.forms.TestcaseForms
import org.ieee_passau.models.{EvalTask, EvalTasks}
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.libs.Files.TemporaryFile
import play.api.mvc._

import scala.reflect.io.File

object EvalTaskController extends Controller with PermissionCheck {

  def edit(pid: Int, id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    EvalTasks.byId(id).firstOption.map { task =>
      Ok(org.ieee_passau.views.html.evaltask.edit(pid, id, TestcaseForms.evalTaskForm.fill(task)))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def delete(pid: Int, id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    EvalTasks.filter(_.id === id).delete
    Redirect(org.ieee_passau.controllers.routes.ProblemController.edit(pid))
  }}

  def insert(pid: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.evaltask.insert(pid, TestcaseForms.evalTaskForm))
  }}

  def save(pid: Int): Action[MultipartFormData[TemporaryFile]] = requireAdmin(parse.multipartFormData) { admin => DBAction(parse.multipartFormData) { implicit rs =>
    implicit val sessionUser = Some(admin)
    TestcaseForms.evalTaskForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.evaltask.insert(pid, errorForm))
      },

      newTaskRaw => {
        rs.body.file("program").map { program =>
          val newTask: EvalTask = newTaskRaw.copy(filename = program.filename, file = new File(program.ref.file).toByteArray())
          val id = (EvalTasks returning EvalTasks.map(_.id)) += newTask
          Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id)).flashing("success" -> "Auswertungsschritt %s wurde angelegt".format(newTaskRaw.position))
        } getOrElse {
          BadRequest(org.ieee_passau.views.html.evaltask.insert(pid, TestcaseForms.evalTaskForm.fill(newTaskRaw).withError("program", "Es muss eine Datei angegeben werden")))
        }
      }
    )
  }}

  def update(pid: Int, id: Int): Action[MultipartFormData[TemporaryFile]] = requireAdmin(parse.multipartFormData) { admin => DBAction(parse.multipartFormData) { implicit rs =>
    implicit val sessionUser = Some(admin)
    TestcaseForms.evalTaskForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.evaltask.edit(pid, id, errorForm))
      },

      task => {
        rs.body.file("program").map { program =>
          val newTask = task.copy(filename = program.filename, file = new File(program.ref.file).toByteArray())
          EvalTasks.update(id, newTask)
          Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id)).flashing("success" -> "Auswertungsschritt %s wurde aktualisiert".format(task.position))
        } getOrElse {
          val oldTask = EvalTasks.byId(id).first
          val newTask = task.copy(filename = oldTask.filename, file = oldTask.file)
          EvalTasks.update(id, newTask)
          Redirect(org.ieee_passau.controllers.routes.EvalTaskController.edit(pid, id)).flashing("success" -> "Auswertungsschritt %s wurde aktualisiert".format(task.position))
        }
      }
    )
  }}
}
