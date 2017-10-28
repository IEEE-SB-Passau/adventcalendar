package org.ieee_passau.controllers

import org.ieee_passau.forms.UserForms
import org.ieee_passau.models.{User, Users}
import org.ieee_passau.utils.{PasswordHasher, PermissionCheck}
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
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
        BadRequest(org.ieee_passau.views.html.user.login(errorForm))
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
        val createdUser: User = registration.makeUser
        val link = org.ieee_passau.controllers.routes.UserController.activate(createdUser.activationToken.get)
          .absoluteURL(secure = play.Configuration.root().getBoolean("application.https", false))
        // TODO i18n
        val regMail = Email(
          "IEEE Adventskalender Registrierung",
          "IEEE Adventskalender <adventskalender@ieee.students.uni-passau.de>",
          List(createdUser.email),
          Some("Hallo " + createdUser.username +
            ",\n\nKlicke bitte folgenden Link um deinen Accout beim IEEE Adventskalender zu aktivieren:\n" +
            link + "\n\nViel Spaß beim Mitmachen,\nDas Adventskalender-Team")
        )
        MailerPlugin.send(regMail)
        Users += createdUser
        Redirect(org.ieee_passau.controllers.routes.UserController.login())
          .flashing("success" -> Messages("user.register.error.existing", createdUser.username))
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
        //TODO i18n
        val user = Users.byUsername(username).first
        val token = PasswordHasher.generateUrlString()
        val link = org.ieee_passau.controllers.routes.UserController.editPassword(token)
          .absoluteURL(secure = play.Configuration.root().getBoolean("application.https", false))
        val regMail = Email(
          "IEEE Adventskalender Benutzerservice",
          "IEEE Adventskalender <adventskalender@ieee.students.uni-passau.de>",
          List(user.email),
          Some("Hallo " + user.username +
            ",\n\nKlicke bitte folgenden Link um deinen Passowort beim IEEE Adventskalender zu zurückzusetzen:\n" +
            link + "\n\nWeiterhin viel Spaß beim Mitmachen,\nDas Adventskalender-Team")
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
            .flashing("success" -> Messages("user.login.messeage"))
        }
      )
    }
  }}

  def updateLang(lang: String): Action[AnyContent] = DBAction { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)

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
