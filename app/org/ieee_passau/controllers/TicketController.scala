package org.ieee_passau.controllers

import java.util.Date

import org.ieee_passau.forms.ProblemForms
import org.ieee_passau.models.{Problems, Ticket, Tickets, Users}
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
import play.api.libs.mailer.{Email, MailerPlugin}
import play.api.mvc._

object TicketController extends Controller with PermissionCheck {

  def index: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    val responses = for {
      t <- Tickets if t.responseTo.?.isDefined
    } yield t.responseTo

    val list = (for {
      t <- Tickets if t.responseTo.?.isEmpty
      u <- Users if u.id === t.userId
      p <- Problems if p.id === t.problemId
    } yield (t, u, p)).sortBy(_._1.created.desc).list
      .map(q => (q._1, q._2, q._3, responses.filter(_ === q._1.id).firstOption.isDefined))

    Ok(org.ieee_passau.views.html.ticket.index(list))
  }}

  def view(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Tickets.byId(id).firstOption.map { ticket =>
      val user = Users.byId(ticket.userId.getOrElse(-1)).firstOption
      val problem = Problems.byId(ticket.problemId.getOrElse(-1)).firstOption
      val answers = for {
        t <- Tickets if t.responseTo === ticket.id
        u <- Users if u.id === t.userId
      } yield (t, u.username)
      // TODO handle no element
      Ok(org.ieee_passau.views.html.ticket.view((ticket, user.get, problem.get), answers.list,
        ProblemForms.ticketForm.bind(Map("public" -> "true")).discardingErrors))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def submitTicket(door: Int): Action[AnyContent] = requireLogin { user => DBAction { implicit rs =>
    implicit val sessionUser = Some(user)
    val now = new Date()
    ProblemForms.ticketForm.bindFromRequest.fold(
      errorForm => {
        Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
          .flashing("danger" -> Messages("ticket.create.error"))
      },
      ticket => {
        val problem = Problems.byDoor(door).first
        val language = request2lang
        val id = (Tickets returning Tickets.map(_.id)) +=
          Ticket(None, problem.id, sessionUser.get.id, None, ticket.text, public = false, now, language)

        //TODO i18n with 'language'
        val email = Email(
          subject = "Adventskalender Frage zu Aufgabe " + problem.door + " " + problem.title,
          from = sessionUser.get.username + " @ Adventskalender <adventskalender@ieee.students.uni-passau.de>",
          to = List("adventskalender@ieee.students.uni-passau.de"),
          bodyText = Some(ticket.text + "\n\n" + "Antworten: \n" + org.ieee_passau.controllers.routes.TicketController.view(id).absoluteURL(play.Configuration.root().getBoolean("application.https", false)))
        )
        MailerPlugin.send(email)

        Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
          .flashing("success" -> Messages("ticket.create.message"))
      }
    )
  }}

  def answerTicket(id: Int): Action[AnyContent] = requireAdmin{ admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    val now = new Date()
    ProblemForms.ticketForm.bindFromRequest.fold(
      errorForm => {
        Redirect(org.ieee_passau.controllers.routes.TicketController.index())
          .flashing("danger" -> Messages("ticket.answer.error"))
      },
      ticket => {
        val parent = Tickets.byId(id).firstOption
        if (parent.isDefined) {
          val problem = Problems.byId(parent.get.problemId.get).firstOption
          val recipient = Users.byId(parent.get.userId.get).firstOption
          if (problem.isDefined && recipient.isDefined) {

            Tickets += Ticket(None, parent.get.problemId, sessionUser.get.id, Some(id), ticket.text, public = ticket.public, now, parent.get.language)
            val updated = parent.get.copy(public = ticket.public)
            Tickets.update(id, updated)

            //TODO i18n with 'parent.get.language'
            val email = Email(
              subject = "Adventskalender Antwort auf die Frage zu Aufgabe " + problem.get.door + " " + problem.get.title,
              from = sessionUser.get.username + " @ Adventskalender <adventskalender@ieee.students.uni-passau.de>",
              to = List(recipient.get.email),
              cc = List("adventskalender@ieee.students.uni-passau.de"),
              bodyText = Some(ticket.text)
            )
            MailerPlugin.send(email)

            Redirect(org.ieee_passau.controllers.routes.TicketController.index())
              .flashing("success" -> Messages("ticket.answer.message"))
          } else {
            Redirect(org.ieee_passau.controllers.routes.TicketController.index())
              .flashing("danger" -> Messages("ticket.answer.error"))
          }
        } else {
          Redirect(org.ieee_passau.controllers.routes.TicketController.index())
            .flashing("danger" -> Messages("ticket.answer.error"))
        }
      }
    )
  }}

  def delete(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Tickets.filter(_.id === id).delete
    Redirect(org.ieee_passau.controllers.routes.TicketController.index())
  }}
}
