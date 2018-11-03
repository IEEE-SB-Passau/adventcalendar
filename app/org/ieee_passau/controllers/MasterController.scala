package org.ieee_passau.controllers

import com.google.inject.Inject
import org.ieee_passau.models.{Guest, Permission, User}
import org.ieee_passau.utils.UserHelper
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.I18nSupport
import play.api.mvc._
import slick.jdbc.JdbcProfile

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class MasterController @Inject()(private val dbConfigProvider: DatabaseConfigProvider,
                                 private val components: MessagesControllerComponents,
                                 implicit private val ec: ExecutionContext
                                ) extends MessagesAbstractController(components) with I18nSupport {
  implicit val db: Database = dbConfigProvider.get[JdbcProfile].db

  /**
    * Requires the given permission level to execute the given action
    *
    * @param level the permission level
    * @param f     the action to carry out
    * @return an asynchronous action
    */

  def requirePermission(level: Permission)(f: => Option[User] => Action[AnyContent])(implicit db: Database): Action[AnyContent] = Action.async { implicit rs =>
    UserHelper.getUserFromRequest(rs).flatMap {
      case Some(u) if u.active && u.permission.includes(level) => f(Some(u))(rs)
      case None if Guest.includes(level) => f(None)(rs)
      case _ => Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()(rs.flash, None, rs, rs.messages)))
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

  def requirePermission[A](level: Permission, bp: BodyParser[A])(f: => Option[User] => Action[A])(implicit db: Database): Action[A] = Action.async(bp) { implicit rs =>
    UserHelper.getUserFromRequest(rs).flatMap({
      case Some(u) if u.active && u.permission.includes(level) => f(Some(u))(rs)
      case None if Guest.includes(level) => f(None)(rs)
      case _ => Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()(rs.flash, None, rs, rs.messages)))
    })
  }
}
