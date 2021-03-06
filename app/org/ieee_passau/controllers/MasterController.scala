package org.ieee_passau.controllers

import java.nio.file.Paths

import com.google.inject.Inject
import org.ieee_passau.models.{Everyone, Guest, Permission, User}
import org.ieee_passau.utils.UserHelper
import play.api.Mode.Prod
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import play.api.{Configuration, Environment, Logger}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class MasterController @Inject()(private val dbConfigProvider: DatabaseConfigProvider,
                                 private val components: MessagesControllerComponents,
                                 implicit private val ec: ExecutionContext,
                                 private val config: Configuration,
                                 private val env: Environment
                                ) extends MessagesAbstractController(components) with I18nSupport {
  implicit val db: Database = dbConfigProvider.get[JdbcProfile].db
  implicit val log: Logger = Logger(this.getClass)

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

  def javascriptRoutes: Action[AnyContent] = Action { implicit request =>
    val res = Ok(JavaScriptReverseRouter("jsRoutes")(
      org.ieee_passau.controllers.routes.javascript.MainController.codeEditor,
      org.ieee_passau.controllers.routes.javascript.CmsController.calendar,
      org.ieee_passau.controllers.routes.javascript.CmsController.content,
      org.ieee_passau.controllers.routes.javascript.UserController.dismissNotification
    )).as("text/javascript")
    if (env.mode == Prod) res.withHeaders(CACHE_CONTROL -> "public, max-age=21600")
    else res
  }

  /**
    * This is mainly used in dev mode, in prod the reverse proxy should serve these files.
    *
    * @param filename the file to serve
    */
  def staticFile(filename: String): Action[AnyContent] = requirePermission(Everyone) { implicit user => Action { implicit request =>
    config.getOptional[String]("play.assets.staticPath")
      .fold(NotFound(org.ieee_passau.views.html.errors.e404())) { source =>
        val file = Paths.get(source, filename).toFile
        if (file.exists())
          if (env.mode == Prod) Ok.sendFile(file).withHeaders(CACHE_CONTROL -> "public, max-age=21600")
          else Ok.sendFile(file)
        else NotFound(org.ieee_passau.views.html.errors.e404())
      }
  }}
}
