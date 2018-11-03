package org.ieee_passau

import java.io.{PrintWriter, StringWriter}

import com.google.inject.{Inject, Provider}
import org.ieee_passau.models.User
import org.ieee_passau.utils.PermissionCheck
import play.api._
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.Results._
import play.api.mvc.{Flash, RequestHeader, Result}
import play.api.routing.Router
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ErrorHandler @Inject()(env: Environment,
                             config: Configuration,
                             sourceMapper: OptionalSourceMapper,
                             router: Provider[Router],
                             mailerClient: MailerClient,
                             dbConfigProvider: DatabaseConfigProvider,
                             val messagesApi: MessagesApi
                            ) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) with I18nSupport {
  implicit val db: Database = dbConfigProvider.get[JdbcProfile].db

  override protected def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
    PermissionCheck.getUserFromRequest(request).map(maybeUser => {
      implicit val rs: RequestHeader = request
      implicit val flash: Flash = rs.flash
      implicit val sessionUser: Option[User] = maybeUser
      val id = exception match {
        case pex: PlayException =>
          Some(pex.id)
        case _ =>
          None
      }

      if (play.Configuration.root().getBoolean("logging.errormail.active", false)) {
        try {
          val sw = new StringWriter
          exception.printStackTrace(new PrintWriter(sw))

          var bodyText = if (sessionUser.isDefined) sessionUser.get.username + "\n\n" else "No user logged in\n\n"
          bodyText += Some(sw.toString)
          val recipients = config.getStringList("logging.errormail.recipient").map(_.asScala).getOrElse(List("adventskalender@ieee.students.uni-passau.de"))

          val errorMail = Email(
            subject = "Adventskalender Error",
            from = "IEEE Adventskalender <adventskalender@ieee.students.uni-passau.de>",
            to = recipients,
            bodyText = Some(bodyText)
          )
          mailerClient.send(errorMail)
        } catch {
          case _: Throwable => // if mail fails, just ignore
        }
      }

      InternalServerError(views.html.errors.e500(id))
    })
  }

  override protected def onBadRequest(request: RequestHeader, message: String): Future[Result] = {
    PermissionCheck.getUserFromRequest(request).map(maybeUser => {
      implicit val rs: RequestHeader = request
      implicit val sessionUser: Option[User] = maybeUser
      implicit val flash: Flash = rs.flash
      InternalServerError(views.html.errors.e404())
    })
  }

  override protected def onNotFound(request: RequestHeader, message: String): Future[Result] = {
    PermissionCheck.getUserFromRequest(request).map(maybeUser => {
      implicit val rs: RequestHeader = request
      implicit val sessionUser: Option[User] = maybeUser
      implicit val flash: Flash = rs.flash
      InternalServerError(views.html.errors.e404())
    })
  }
}
