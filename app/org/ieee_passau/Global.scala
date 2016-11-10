package org.ieee_passau

import java.io.{PrintWriter, StringWriter}

import evaluation.Evaluator
import play.api.Play.current
import play.api._
import play.api.libs.mailer._
import play.api.mvc._
import play.filters.csrf.CSRF.ErrorHandler
import play.filters.csrf._
import utils.PermissionCheck

import scala.collection.JavaConverters._
import scala.concurrent.Future

object Global extends WithFilters(CSRFFilter(errorHandler = new CSRFFilterError())) with GlobalSettings with PermissionCheck {

  override def onHandlerNotFound(request: RequestHeader): Future[Result] = {
    implicit val sessionUser = getUserFromSession(request.session)
    implicit val flash = Flash.emptyCookie
    Future.successful(NotFound(views.html.errors.e404()))
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val sessionUser = getUserFromSession(request.session)
    implicit val flash = Flash.emptyCookie
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
    implicit val sessionUser = getUserFromSession(request.session)
    implicit val flash = Flash.emptyCookie

    Future.successful(InternalServerError(views.html.errors.e404()))
  }

  override def onStart(app: Application): Unit = {
    super.onStart(app)

    // Start evaluator
    Evaluator.start
  }
}

class CSRFFilterError extends ErrorHandler with PermissionCheck {
  override def handle(req: RequestHeader, msg: String): Result = {
    implicit val sessionUser = getUserFromSession(req.session)
    implicit val flash = Flash()
    BadRequest(views.html.errors.eText("Das Formular ist abgelaufen. Lade die Seite bitte neu!"))
  }
}
