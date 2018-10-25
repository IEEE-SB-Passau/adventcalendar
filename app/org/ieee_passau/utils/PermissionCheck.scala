package org.ieee_passau.utils

import org.ieee_passau.models._
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.DB
import play.api.mvc._

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
  def getUserFromRequest(request: RequestHeader): Option[User] = {
    request2session(request).get("user").map { uid =>
      DB.withSession[Option[User]] { implicit s: play.api.db.slick.Session =>
        Users.byId(uid.toInt).firstOption;
      }
    } getOrElse {
      request.getQueryString("token").flatMap { token =>
        DB.withSession[Option[User]] { implicit s: play.api.db.slick.Session =>
          Users.byToken(token).list.headOption
          // only internal aka backend users can authenticate with a token
        }
      } filter { maybeUser => maybeUser.permission == Internal }
    }
  }

  /**
    * Requires the given permission level to execute the given action
    *
    * @param level the permission level
    * @param f     the action to carry out
    * @return an asynchronous action
    */
  def requirePermission(level: Permission)(f: => Option[User] => Action[AnyContent]): Action[AnyContent] = Action.async { implicit rs =>
    implicit val user = getUserFromRequest(rs)
    if (user.isEmpty && level == Guest) {
      f(None)(rs)
    } else if (user.isEmpty) {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    } else if (user.get.active && user.get.permission.includes(level)) {
      f(user)(rs)
    } else {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
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
  def requirePermission[A](level: Permission, bp: BodyParser[A])(f: => Option[User] => Action[A]): Action[A] = Action.async(bp) { implicit rs =>
    implicit val user = getUserFromRequest(rs)
    if ((user.isEmpty && level == Guest) || (user.isDefined && user.get.active && user.get.permission == level)) {
      f(user)(rs)
    } else {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    }
  }
}
