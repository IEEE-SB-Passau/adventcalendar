package org.ieee_passau.utils

import org.ieee_passau.models.{Guest, Permission, User, Users, _}
import play.api.i18n.MessagesApi
import play.api.mvc._
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * This class provides handy wrappers for actions to check if a user is authorized to do something.
  */
trait PermissionCheck extends Controller {

  /**
    * Get a user based on the given request. First tries to get a user from the session, if no user (valid or invalid)
    * is present, tries to use a given access token to identify the user, internal users only.
    *
    * @param request the request
    * @return optionally the user in the session ot specified by the token
    */
  def getUserFromRequest(request: RequestHeader)(implicit db: Database): Future[Option[User]] = {
    request2session(request).get("user").map { uid =>
      db.run(Users.byId(uid.toInt).result.headOption).flatMap {
        case Some(x) => Future.successful(Some(x))
        case _ =>
          request.getQueryString("token").map { token =>
            db.run(Users.byToken(token).result.headOption).map {
              // only internal aka backend users can authenticate with a token
              case Some(u) if u.permission == Internal => Some(u)
              case _ => None
            }
          }.getOrElse(Future.successful(None))
      }
    }.getOrElse(Future.successful(None))
  }

  /**
    * Requires the given permission level to execute the given action
    *
    * @param level the permission level
    * @param f     the action to carry out
    * @return an asynchronous action
    */

  def requirePermission(level: Permission)(f: => Option[User] => Action[AnyContent])(implicit db: Database, messagesApi: MessagesApi): Action[AnyContent] = Action.async { implicit rs =>
    getUserFromRequest(rs).flatMap {
      case Some(u) if u.active && u.permission.includes(level) => f(Some(u))(rs)
      case None if Guest.includes(level) => f(None)(rs)
      case _ => Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()(request2flash, None, rs, messagesApi, request2lang)))
    }
  }

  /**
    * Requires the given permission level to execute the given action
    *
    * @param level the permission level
    * @param bp    the content handler type
    * @param f     the action to carry out
    * @tparam A the action type
    * @return an asynchronous action
    */

  def requirePermission[A](level: Permission, bp: BodyParser[A])(f: => Option[User] => Action[A])(implicit db: Database, messagesApi: MessagesApi): Action[A] = Action.async(bp) { implicit rs =>
    getUserFromRequest(rs).flatMap({
      case Some(u) if u.active && u.permission.includes(level) => f(Some(u))(rs)
      case None if Guest.includes(level) => f(None)(rs)
      case _ => Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()(request2flash, None, rs, messagesApi, request2lang)))
    })
  }
}
