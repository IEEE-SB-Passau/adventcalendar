package org.ieee_passau.controllers

import java.util.Date

import com.google.inject.Inject
import org.ieee_passau.models.DateSupport.dateMapper
import org.ieee_passau.models._
import org.ieee_passau.utils.StringHelper.encodeEmailName
import org.ieee_passau.utils.{FormHelper, PermissionCheck}
import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional, text}
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.I18nSupport
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc._
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TicketController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                 components: MessagesControllerComponents,
                                 mailerClient: MailerClient
                                ) extends ControllerWithDBAndI18n(dbConfigProvider, components) with PermissionCheck with I18nSupport {

  def index: Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    val responsesQuery = for {
      t <- Tickets if t.responseTo.?.isDefined
    } yield t.responseTo.?

    val listQuery = (for {
      t <- Tickets if t.responseTo.?.isEmpty
      u <- Users if u.id === t.userId
      p <- Problems if p.id === t.problemId
    } yield (t, u, p)).sortBy(_._1.created.desc)
    // TODO join in one query?
    db.run(responsesQuery.result).zip(db.run(listQuery.to[List].result)).map { tuple =>
      Ok(org.ieee_passau.views.html.ticket.index(tuple._2.map(q => (q._1, q._2, q._3, tuple._1.contains(q._1.id)))))
    }
  }}

  def view(id: Int): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    db.run(Tickets.byId(id).result.headOption).flatMap {
      case Some(ticket) =>
        db.run(Users.byId(ticket.userId.get).result.headOption).flatMap {
          case Some(user) => db.run(Problems.byId(ticket.problemId.getOrElse(-1)).result.headOption).flatMap {
            case Some(problem) =>
              val answersQuery = for {
                t <- Tickets if t.responseTo === ticket.id
                u <- Users if u.id === t.userId
              } yield (t, u.username)
              db.run(answersQuery.to[List].result).map { answers =>
                Ok(org.ieee_passau.views.html.ticket.view((ticket, user, problem), answers,
                  FormHelper.ticketForm.bind(Map("public" -> "true")).discardingErrors))
              }
            case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
          }
          case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
        }
      case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }}

  def submitTicket(door: Int): Action[AnyContent] = requirePermission(Contestant) { implicit user => Action.async { implicit rs =>
    FormHelper.ticketForm.bindFromRequest.fold(
      _ => {
        Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
          .flashing("danger" -> rs.messages("ticket.create.error")))
      },
      ticket => {
        val language = rs.lang

        db.run(Problems.byDoor(door).result.headOption).flatMap {
          case Some(problem) =>
            db.run(ProblemTranslations.byProblemLang(problem.id.get, language).result.headOption).flatMap { maybeProblemTitle =>
              val problemTitle = maybeProblemTitle.fold(problem.title)(_.title)
              val now = new Date()
              db.run((Tickets returning Tickets.map(_.id)) += Ticket(None, problem.id, user.get.id, None, ticket.text, public = false, now, language)).map { id =>
                val email = Email(
                  subject = rs.messages("email.header") + " " +  rs.messages("ticket.title") + " zu " + rs.messages("problem.title") + " " + problem.door + ": " + problemTitle,
                  from = encodeEmailName(user.get.username) + " @ " + play.Configuration.root().getString("email.from"),
                  to = List(play.Configuration.root().getString("email.from")),
                  bodyText = Some(ticket.text + "\n\n" + rs.messages("ticket.answer") + ": " + org.ieee_passau.controllers.routes.TicketController.view(id).absoluteURL(play.Configuration.root().getBoolean("application.https", false)))
                )
                mailerClient.send(email)

                Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
                  .flashing("success" -> rs.messages("ticket.create.message"))
              }
            }

          case _ =>
            Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
              .flashing("danger" -> rs.messages("ticket.create.error")))
        }
      }
    )
  }}

  def answerTicket(id: Int): Action[AnyContent] = requirePermission(Moderator) { implicit mod => Action.async { implicit rs =>
    FormHelper.ticketForm.bindFromRequest.fold(
      _ => {
        Future.successful(Redirect(org.ieee_passau.controllers.routes.TicketController.index())
          .flashing("danger" -> rs.messages("ticket.answer.error")))
      },
      ticket => {
        db.run(Tickets.byId(id).result.headOption).flatMap {
          case Some(parent) =>
            db.run(Problems.byId(parent.problemId.get).result.headOption).flatMap {
              case Some(problem) =>
                db.run(Users.byId(parent.userId.get).result.headOption).flatMap {
                  case Some(recipient) =>
                    val msgLang = parent.language
                    val now = new Date()
                    db.run(Tickets += Ticket(None, parent.problemId, mod.get.id, Some(id), ticket.text, public = ticket.public, now, parent.language))
                    val updated = parent.copy(public = ticket.public)
                    Tickets.update(updated.id.get, updated)
                    db.run(ProblemTranslations.byProblemLang(problem.id.get, msgLang).result.headOption).map { maybeProblemTitle =>
                      val problemTitle = maybeProblemTitle.fold(problem.title)(_.title)
                      val email = Email(
                        subject = rs.messagesApi("email.header")(msgLang) + " " + rs.messagesApi("email.answer.subject", rs.messagesApi("ticket.title")(msgLang) +  " zu " + rs.messagesApi("problem.title")(msgLang) + " " + problem.door + ": " + problemTitle)(msgLang),
                        from = encodeEmailName(mod.get.username) + " @ " + play.Configuration.root().getString("email.from"),
                        to = List(recipient.email),
                        cc = List(play.Configuration.root().getString("email.from")),
                        bodyText = Some(ticket.text)
                      )
                      mailerClient.send(email)
                      Redirect(org.ieee_passau.controllers.routes.TicketController.index())
                        .flashing("success" -> rs.messages("ticket.answer.message"))
                    }
                  case _ => Future.successful(Redirect(org.ieee_passau.controllers.routes.TicketController.index())
                    .flashing("danger" -> rs.messages("ticket.answer.error")))
                }
              case _ => Future.successful(Redirect(org.ieee_passau.controllers.routes.TicketController.index())
                .flashing("danger" -> rs.messages("ticket.answer.error")))
            }
          case _ => Future.successful(Redirect(org.ieee_passau.controllers.routes.TicketController.index())
            .flashing("danger" -> rs.messages("ticket.answer.error")))
        }
      }
    )
  }}

  def delete(id: Int): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    db.run(Tickets.filter(_.id === id).delete).map(_ =>
      Redirect(org.ieee_passau.controllers.routes.TicketController.index())
    )
  }}

  def feedback: Action[AnyContent] = requirePermission(Contestant) { implicit user => Action { implicit rs =>
    Ok(org.ieee_passau.views.html.general.feedback(feedbackForm))
  }}

  def submitFeedback: Action[AnyContent] = requirePermission(Contestant) { implicit user => Action.async { implicit rs =>
    feedbackForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.general.feedback(errorForm)))
      },
      fb => {
        db.run(Feedbacks += Feedback(None, user.get.id.get, fb.rating, fb.pro, fb.con, fb.freetext)).map(_ =>
          Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
          .flashing("success" -> rs.messages("feedback.submit.message"))
        )
      }
    )
  }}

  def feedbackForm = Form(
    mapping(
      "id" -> optional(number),
      "user_id" -> number,
      "rating" -> number(1, 5),
      "pro" -> optional(text),
      "con" -> optional(text),
      "freetext" -> optional(text)
    )(Feedback.apply)(Feedback.unapply)
  )
}
