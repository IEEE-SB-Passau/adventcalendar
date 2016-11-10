package org.ieee_passau.controllers

import org.ieee_passau.forms.UserForms
import org.ieee_passau.models.{User, Users}
import org.ieee_passau.utils.{PasswordHasher, PermissionCheck}
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
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
        .flashing("success" -> "Du bis nun als %s angemeldet.".format(user.username))
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
        val msg = "Willkommen zurück " + user.username + "!"
        val uid = user.id.get.toString
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("success" -> msg)
          .withSession("user" -> uid)
      }
    )
  }}

  def logout: Action[AnyContent] = requireLogin { user => Action { implicit rs =>
    implicit val sessionUser = Some(user)
    Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
      .flashing("success" -> "Bis bald %s!".format(user.username))
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
        val link = org.ieee_passau.controllers.routes.UserController.activate(createdUser.activationToken.get).absoluteURL(secure = play.Configuration.root().getBoolean("application.https", false))
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
          .flashing("success" -> "Benutzer %s wurde angelegt. Bitte verifiziere deine E-Mailadresse mit dem zugesandten Link.".format(createdUser.username))
      }
    )
  }}

  def activate(token: String): Action[AnyContent] = requireGuest { DBAction { implicit rs =>
    val maybeUser = Users.byToken(token).firstOption
    implicit val sessionUser = maybeUser
    maybeUser match {
      case Some(user) =>
        val msg = "Willkommen " + user.username + "!"
        Users.update(user.id.get, user.copy(active = true, activationToken = None))
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("success" -> msg)
          .addingToSession("user" -> user.id.get.toString)

      case None =>
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("warning" -> "Dieser Link ist nicht gültig!")
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
        val link = org.ieee_passau.controllers.routes.UserController.editPassword(token).absoluteURL(secure = play.Configuration.root().getBoolean("application.https", false))
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
          .flashing("success" -> "Es wurde eine E-Mail an die angegeben Adresse gesendet. Bitte benutze den Link um dein Passwort zurückzusetzen")
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
          .flashing("danger" -> "Dieser Link ist nicht gültig!")
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
          Users.update(user.id.get, user.copy(password = pwh, activationToken = None))

          val msg = "Willkommen zurück " + user.username + "!"
          Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
            .flashing("success" -> msg)
        }
      )
    }
  }}

  def update(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    UserForms.userForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.user.edit(id, errorForm))
      },
      user => {
        val pwh = if (user.password.isEmpty) Users.byId(id).firstOption.get.password else PasswordHasher.hashPassword(user.password)
        Users.update(id, user.copy(password = pwh))
        Redirect(org.ieee_passau.controllers.routes.UserController.edit(id)).flashing("success" -> "Benutzer %s wurde aktualisiert".format(user.username))
      }
    )
  }}
}
