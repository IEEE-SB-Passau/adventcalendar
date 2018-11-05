package org.ieee_passau.utils

import org.ieee_passau.models.{User, Users, _}
import play.api.mvc._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

object UserHelper {

  /**
    * Get a user based on the given request. First tries to get a user from the session, if no user (valid or invalid)
    * is present, tries to use a given access token to identify the user, internal users only.
    *
    * @param request the request
    * @return optionally the user in the session ot specified by the token
    */
  def getUserFromRequest(request: RequestHeader)(implicit db: Database, ec: ExecutionContext): Future[Option[User]] = {
    request.session.get("user") match {
      case Some(uid) => db.run(Users.byId(uid.toInt).result.headOption)
      case _ =>
        request.getQueryString("token").map { token =>
          db.run(Users.byToken(token).result.headOption).map {
            // only internal aka backend users can authenticate with a token
            case Some(u) if u.permission == Internal => Some(u)
            case _ => None
          }
        }.getOrElse(Future.successful(None))
    }
  }
}
