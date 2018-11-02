package org.ieee_passau

import com.google.inject.Inject
import org.ieee_passau.models.User
import org.ieee_passau.utils.PermissionCheck
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc._
import play.filters.csrf.CSRF
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CSRFFilterErrorHandler @Inject() (dbConfigProvider: DatabaseConfigProvider, implicit val messagesApi: MessagesApi) extends CSRF.ErrorHandler with I18nSupport {
  implicit val db: Database = dbConfigProvider.get[JdbcProfile].db

  override def handle(request: RequestHeader, msg: String): Future[Result] = {
    PermissionCheck.getUserFromRequest(request).map(maybeUser => {
      implicit val rs: RequestHeader = request
      implicit val sessionUser: Option[User] = maybeUser
      implicit val flash: Flash = Flash()
      BadRequest(views.html.errors.eText(rs.messages.apply("error.csrf"))(flash, sessionUser, rs, rs.messages))
    })
  }
}
