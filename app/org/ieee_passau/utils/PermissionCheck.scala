package org.ieee_passau.utils

import org.ieee_passau.models.{Guest, Permission, User, Users}
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.DB
import play.api.mvc._

import scala.concurrent.Future

/**
  * This class provides handy wrappers for actions to check if a user is authorized to do something.
  */
trait PermissionCheck extends Controller {

  def getUserFromSession(session: play.api.mvc.Session): Option[User] = {
    val maybeUid = session.get("user")
    if (maybeUid.isEmpty) {
      return None
    }

    val uid = maybeUid.get.toInt

    DB.withSession[Option[User]] { implicit s: play.api.db.slick.Session =>
      Users.byId(uid).firstOption;
    }
  }

  /**
    * The action can only be accessed by an authenticated, active administrator.
    *
    * @param f action to carry out.
    */
  def requirePermission(level: Permission)(f: => User => Action[AnyContent]): Action[AnyContent] = Action.async { implicit rs =>
    implicit val user = getUserFromSession(rs.session)
    if (user.isEmpty && level == Guest) {
      f(null)(rs)
    } else if (user.isEmpty) {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    } else if (user.get.active && user.get.permission.includes(level)) {
      f(user.get)(rs)
    } else {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    }
  }

  /**
    * The action can only be accessed by an authenticated, active administrator.
    *
    * @param f action to carry out.
    */
  def requirePermission[A](level: Permission, bp: BodyParser[A])(f: => User => Action[A]): Action[A] = Action.async(bp) { implicit rs =>
    implicit val user = getUserFromSession(rs.session)
    if ((user.isEmpty && level == Guest) || (user.get.active && user.get.permission == level)) {
      f(user.get)(rs)
    } else {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    }
  }
}
