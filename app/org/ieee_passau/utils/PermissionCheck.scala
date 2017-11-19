package org.ieee_passau.utils

import org.ieee_passau.models.{User, Users}
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
    * The action can only be accessed by a guest.
    *
    * @param f action to carry out.
    */
  def requireGuest(f: => Action[AnyContent]): Action[AnyContent] = Action.async { implicit rs =>
    implicit val user = getUserFromSession(rs.session)
    if (user.isDefined) {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    } else {
      f(rs)
    }
  }

  /**
    * The action can only be accessed by an authenticated, active administrator.
    *
    * @param f action to carry out.
    */
  def requireAdmin(f: => User => Action[AnyContent]): Action[AnyContent] = Action.async { implicit rs =>
    implicit val user = getUserFromSession(rs.session)
    if (user.isEmpty || !user.get.active || !user.get.admin) {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    } else {
      f(user.get)(rs)
    }
  }

  /**
    * The action can only be accessed by an authenticated, active administrator.
    *
    * @param f action to carry out.
    */
  def requireAdmin[A](bp: BodyParser[A])(f: => User => Action[A]): Action[A] = Action.async(bp) { implicit rs =>
    implicit val user = getUserFromSession(rs.session)
    if (user.isEmpty || !user.get.active || !user.get.admin) {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    } else {
      f(user.get)(rs)
    }
  }

  /**
    * The action can only be accessed by an authenticated, active system user (i.e the evaluation system).
    *
    * @param f action to carry out.
    */
  def requireSystem(f: => User => Action[AnyContent]): Action[AnyContent] = Action.async { implicit rs =>
    implicit val user = getUserFromSession(rs.session)
    if (user.isEmpty || !user.get.active || !user.get.system) {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    } else {
      f(user.get)(rs)
    }
  }

  /**
    * The action can only be accessed by an authenticated, active user.
    *
    * @param f action to carry out.
    */
  def requireLogin(f: => User => Action[AnyContent]): Action[AnyContent] = Action.async { implicit rs =>
    implicit val user = getUserFromSession(rs.session)
    if (user.isEmpty || !user.get.active) {
      Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
    } else {
      f(user.get)(rs)
    }
  }
}
