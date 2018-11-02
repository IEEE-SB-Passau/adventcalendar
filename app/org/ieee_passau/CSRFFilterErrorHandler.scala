package org.ieee_passau

import com.google.inject.Inject
import org.ieee_passau.models.User
import org.ieee_passau.utils.PermissionCheck
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.MessagesApi
import play.api.mvc.{Flash, RequestHeader, Result}
import play.filters.csrf.CSRF
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CSRFFilterErrorHandler @Inject() (dbConfigProvider: DatabaseConfigProvider, val messagesApi: MessagesApi) extends CSRF.ErrorHandler with PermissionCheck {
  private implicit val db: Database = dbConfigProvider.get[JdbcProfile].db

  override def handle(request: RequestHeader, msg: String): Future[Result] = {
    getUserFromRequest(request).map(maybeUser => {
      implicit val sessionUser: Option[User] = maybeUser
      implicit val flash: Flash = Flash()
      implicit val rs: RequestHeader = request
      BadRequest(views.html.errors.eText(messagesApi("error.csrf"))(flash, sessionUser, rs, messagesApi, request2lang))
    })
  }
}
