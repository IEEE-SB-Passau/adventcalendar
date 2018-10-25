package org.ieee_passau

import java.io.{PrintWriter, StringWriter}

import org.ieee_passau.evaluation.Evaluator
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api._
import play.api.i18n.Messages
import play.api.libs.mailer._
import play.api.mvc._
import play.filters.csrf.CSRF.ErrorHandler
import play.filters.csrf._

import scala.collection.JavaConverters._
import scala.concurrent.Future

object Global extends WithFilters(CSRFFilter(errorHandler = new CSRFFilterError())) with GlobalSettings with PermissionCheck {

  override def onHandlerNotFound(request: RequestHeader): Future[Result] = {
    implicit val sessionUser = getUserFromRequest(request)
    implicit val rs = request
    Future.successful(NotFound(views.html.errors.e404()))
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val sessionUser = getUserFromRequest(request)
    implicit val rs = request
    val id = ex match {
      case pex: PlayException =>
        Some(pex.id)
      case _ =>
        None
    }

    if (play.Configuration.root().getBoolean("logging.errormail.activ", false)) {
      try {
        val sw = new StringWriter
        ex.printStackTrace(new PrintWriter(sw))

        var bodyText = if (sessionUser.isDefined) sessionUser.get.username + "\n\n" else "No user logged in\n\n"
        bodyText += Some(sw.toString)

        val errorMail = Email(
          subject = "Adventskalender Error",
          from = "IEEE Adventskalender <adventskalender@ieee.students.uni-passau.de>",
          to = play.Configuration.root().getStringList("logging.errormail.recipient", List("adventskalender@ieee.students.uni-passau.de").asJava).asScala,
          bodyText = Some(bodyText)
        )
        MailerPlugin.send(errorMail)
      } catch {
        case x: Throwable  => // if mail fails, just ignore
      }
    }

    Future.successful(InternalServerError(views.html.errors.e500(id)))
  }

  override def onBadRequest(request: RequestHeader, error: String): Future[Result] = {
    implicit val sessionUser = getUserFromRequest(request)
    implicit val rs = request

    Future.successful(InternalServerError(views.html.errors.e404()))
  }

  override def onStart(app: Application): Unit = {
    super.onStart(app)

    // Start evaluator
    Evaluator.start()
  }
}

class CSRFFilterError extends ErrorHandler with PermissionCheck {
  override def handle(request: RequestHeader, msg: String): Result = {
    implicit val sessionUser = getUserFromRequest(request)
    implicit val flash = Flash()
    implicit val rs = request
    BadRequest(views.html.errors.eText(Messages("error.csrf"))(flash, sessionUser, rs, request2lang))
  }
}
