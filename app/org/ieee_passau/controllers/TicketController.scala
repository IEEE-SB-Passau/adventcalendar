package org.ieee_passau.controllers

import java.util.Date

import org.ieee_passau.forms.ProblemForms
import org.ieee_passau.models._
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
        val problem = Problems.byDoor(door).firstOption
        val language = request2lang
        if (problem.isDefined) {

          val problemTitle = ProblemTranslations.byProblemLang(problem.get.id.get, language).firstOption.fold(problem.get.title)(_.title)

          val id = (Tickets returning Tickets.map(_.id)) +=
            Ticket(None, problem.get.id, sessionUser.get.id, None, ticket.text, public = false, now, language)

          val email = Email(
            subject = Messages("email.ticket.subject", Messages("ticket.title")(language) + " zu " + Messages("problem.title")(language) + " " + problem.get.door + ": " + problemTitle)(language),
            from = sessionUser.get.username + " @ " + play.Configuration.root().getString("email.from"),
            to = List(play.Configuration.root().getString("email.from")),
            bodyText = Some(ticket.text + "\n\n" + Messages("ticket.answer")(language) + ": " + org.ieee_passau.controllers.routes.TicketController.view(id).absoluteURL(play.Configuration.root().getBoolean("application.https", false)))
          )
          MailerPlugin.send(email)

          Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
            .flashing("success" -> Messages("ticket.create.message"))
        } else {
          Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
            .flashing("danger" -> Messages("ticket.create.error"))
        }
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

            val msgLang = parent.get.language
            val problemTitle = ProblemTranslations.byProblemLang(problem.get.id.get, msgLang).firstOption.fold(problem.get.title)(_.title)
            val email = Email(
              subject = Messages("email.answer.subject", Messages("ticket.title")(msgLang) +  " zu " + Messages("problem.title")(msgLang) + " " + problem.get.door + ": " + problemTitle)(msgLang),
              from = sessionUser.get.username + " @ " + play.Configuration.root().getString("email.from"),
              to = List(recipient.get.email),
              cc = List(play.Configuration.root().getString("email.from")),
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
