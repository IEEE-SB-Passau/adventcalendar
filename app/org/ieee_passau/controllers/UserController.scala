package org.ieee_passau.controllers

import org.apache.commons.mail.EmailException
import org.ieee_passau.forms.UserForms
import org.ieee_passau.models.{User, Users}
import org.ieee_passau.utils.{PasswordHasher, PermissionCheck}
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.{Lang, Messages}
import play.api.libs.mailer._
import play.api.mvc._

object UserController extends Controller with PermissionCheck {

  def index: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.user.index(Users.sortBy(_.id).list))
  }}

  def edit(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Users.byId(id).firstOption.map { user =>
      Ok(org.ieee_passau.views.html.user.edit(id, UserForms.userForm.fill(user)))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def delete(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Users.filter(_.id === id).delete
    Redirect(org.ieee_passau.controllers.routes.UserController.index())
  }}

  def impersonate(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    val maybeUser = Users.byId(id).firstOption

    if (maybeUser.isDefined) {
      val user = maybeUser.get
      Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
        .flashing("success" -> Messages("user.impersonate.message", user.username))
        .withSession("user" -> user.id.get.toString)

    } else {
      NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def login: Action[AnyContent] = requireGuest { Action { implicit rs =>
    implicit val sessionUser = None

    // if no post data, display empty form
    Ok(org.ieee_passau.views.html.user.login(UserForms.loginForm))
  }}

  def authenticate: Action[AnyContent] = requireGuest { Action { implicit rs =>
    implicit val sessionUser = None

    UserForms.loginForm.bindFromRequest.fold(
      errorForm => {
        // Hack to clear the password field
        // set the old username
        val tmp1 = UserForms.loginForm.bind(Map("username" -> errorForm("username").value.getOrElse("")))
        val tmp2 = if (errorForm.hasGlobalErrors)
          // if global error -> login failed due to failed auth, clear the pass to short error
          tmp1.discardingErrors.withGlobalError(Messages("user.login.error"))
        else tmp1
        BadRequest(org.ieee_passau.views.html.user.login(tmp2))
      },

      userLogin => {
        val user: User = userLogin.user.get
        val uid = user.id.get.toString
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("success" -> Messages("user.login.message", user.username))
          .withSession("user" -> uid)
          .withCookies(
            Cookie(play.Play.langCookieName(),
              user.lang.getOrElse(rs.acceptLanguages.headOption.getOrElse(play.api.i18n.Lang.defaultLang).language)
            )
          )
      }
    )
  }}

  def logout: Action[AnyContent] = requireLogin { user => Action { implicit rs =>
    implicit val sessionUser = Some(user)
    Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
      .flashing("success" -> Messages("user.logout.message", user.username))
      .withNewSession;
  }}

  def register: Action[AnyContent] = requireGuest { Action { implicit rs =>
    implicit val sessionUser = None
    Ok(org.ieee_passau.views.html.user.register(UserForms.registrationForm))
  }}

  def create: Action[AnyContent] = requireGuest { DBAction { implicit rs =>
    implicit val sessionUser = None

    UserForms.registrationForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.user.register(errorForm))
      },

      registration => {
        val createdUser: User = registration.makeUser(request2lang)
        val link = org.ieee_passau.controllers.routes.UserController.activate(createdUser.activationToken.get)
          .absoluteURL(secure = play.Configuration.root().getBoolean("application.https", false))
        val regMail = Email(
          subject = Messages("email.header") + " " + Messages("email.register.subject"),
          from = play.Configuration.root().getString("email.from"),
          to = List(createdUser.email),
          bodyText = Some(Messages("email.register.body", createdUser.username, link))
        )
        var id = -1
        try {
          id = (Users returning Users.map(_.id)) += createdUser
          MailerPlugin.send(regMail)

          Redirect(org.ieee_passau.controllers.routes.UserController.login())
            .flashing("success" -> Messages("user.register.error.existing", createdUser.username))
        } catch {
          case _: EmailException =>
            Users.filter(_.id === id).delete
            BadRequest(org.ieee_passau.views.html.user.register(UserForms.registrationForm.fill(registration).withError("invalidEmail", "error.email")))

          case e: Throwable =>
            Users.filter(_.id === id).delete
            throw e
        }
      }
    )
  }}

  def activate(token: String): Action[AnyContent] = requireGuest { DBAction { implicit rs =>
    val maybeUser = Users.byToken(token).firstOption
    implicit val sessionUser = maybeUser
    maybeUser match {
      case Some(user) =>
        Users.update(user.id.get, user.copy(active = true, activationToken = None))
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("success" -> Messages("user.register.message", user.username))
          .addingToSession("user" -> user.id.get.toString)

      case None =>
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("warning" -> Messages("error.invalidlink"))
    }
  }}

  def resetPassword: Action[AnyContent] = requireGuest { Action { implicit rs =>
    implicit val sessionUser = None
    Ok(org.ieee_passau.views.html.user.requestPassword(UserForms.usernameForm.fill("")))
  }}

  def requestPassword: Action[AnyContent] = requireGuest { DBAction { implicit rs =>
    implicit val sessionUser = None

    UserForms.usernameForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.user.requestPassword(errorForm))
      },

      username => {
        val user = Users.byUsername(username).first
        val token = PasswordHasher.generateUrlString()
        val link = org.ieee_passau.controllers.routes.UserController.editPassword(token)
          .absoluteURL(secure = play.Configuration.root().getBoolean("application.https", false))
        val regMail = Email(
          subject = Messages("email.header") + " " + Messages("email.passwordreset.subject"),
          from = play.Configuration.root().getString("email.from"),
          to = List(user.email),
          bodyText = Some(Messages("email.passwordreset.body", user.username, link))
        )
        MailerPlugin.send(regMail)
        Users.update(user.id.get, user.copy(activationToken = Some(token)))

        Redirect(org.ieee_passau.controllers.routes.UserController.login())
          .flashing("success" -> Messages("user.register.verify.message"))
      }
    )
  }}

  def editPassword(token: String): Action[AnyContent] = requireGuest { DBAction { implicit rs =>
    val maybeUser = Users.byToken(token).firstOption
    implicit val sessionUser = maybeUser
    maybeUser match {
      case Some(user) =>
        Users.update(user.id.get, user.copy(active = true))
        Ok(org.ieee_passau.views.html.user.resetPassword(token, UserForms.passwordForm.fill("")))
          .addingToSession("user" -> user.id.get.toString)

      case None =>
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("danger" -> Messages("error.invalidlink"))
    }
  }}

  def updatePassword(token: String): Action[AnyContent] = requireLogin { user => DBAction { implicit rs =>
    implicit val sessionUser = Some(user)

    if (sessionUser.get.activationToken.getOrElse("") != token) {
      Unauthorized(org.ieee_passau.views.html.errors.e403())
    } else {

      UserForms.passwordForm.bindFromRequest.fold(
        errorForm => {
          BadRequest(org.ieee_passau.views.html.user.resetPassword(token, errorForm))
        },

        password => {
          val pwh = PasswordHasher.hashPassword(password)
          val lng = Users.byId(user.id.get).firstOption.get.lang
          Users.update(user.id.get, user.copy(password = pwh, activationToken = None, lang = lng))

          Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
            .flashing("success" -> Messages("user.login.message"))
        }
      )
    }
  }}

  def updateLang(lang: String): Action[AnyContent] = DBAction { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)

    val maybeLang = Lang.get(lang)
    if (maybeLang.isEmpty || !Lang.availables.contains(maybeLang.get)) {
      Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
        .flashing("danger" -> Messages("language.unsupported"))

    } else {
      if (sessionUser.nonEmpty) {
        val user = sessionUser.get
        Users.update(user.id.get, user.copy(lang = Some(lang)))
      }

      val refererUrl = rs.headers.get("referer")
      val cookie = Cookie(play.Play.langCookieName(), lang)
      if (refererUrl.nonEmpty) {
        Redirect(refererUrl.get, 303).withCookies(cookie)
      } else {
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar()).withCookies(cookie)
      }
    }
  }

  def update(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    UserForms.userForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.user.edit(id, errorForm))
      },
      user => {
        val pwh = if (user.password.isEmpty) Users.byId(id).firstOption.get.password else PasswordHasher.hashPassword(user.password)
        val tkn = Users.byId(id).firstOption.get.activationToken
        val lng = Users.byId(id).firstOption.get.lang
        Users.update(id, user.copy(password = pwh, activationToken = tkn, lang = lng))
        Redirect(org.ieee_passau.controllers.routes.UserController.edit(id))
          .flashing("success" -> Messages("user.update.message", user.username))
      }
    )
  }}
}
