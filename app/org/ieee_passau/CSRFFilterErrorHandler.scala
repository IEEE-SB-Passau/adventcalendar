package org.ieee_passau

import com.google.inject.Inject
import org.ieee_passau.models.User
import org.ieee_passau.utils.UserHelper
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc._
import play.filters.csrf.CSRF
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class CSRFFilterErrorHandler @Inject() (val dbConfigProvider: DatabaseConfigProvider,
                                        implicit val messagesApi: MessagesApi,
                                        implicit val ec: ExecutionContext
                                       ) extends CSRF.ErrorHandler with I18nSupport {
  implicit val db: Database = dbConfigProvider.get[JdbcProfile].db

  override def handle(request: RequestHeader, msg: String): Future[Result] = {
    UserHelper.getUserFromRequest(request).map(maybeUser => {
      implicit val rs: RequestHeader = request
      implicit val sessionUser: Option[User] = maybeUser
      implicit val flash: Flash = Flash()
      BadRequest(views.html.errors.eText(rs.messages.apply("error.csrf"))(flash, sessionUser, rs, rs.messages))
    })
  }
}
