package org.ieee_passau.controllers

import com.google.inject.Inject
import org.apache.commons.mail.EmailException
import org.ieee_passau.models.{Admin, _}
import org.ieee_passau.utils.{CaptchaHelper, PasswordHasher, PermissionCheck}
import play.api.Configuration
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.{Lang, MessagesApi}
import play.api.libs.mailer._
import play.api.mvc._
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserController @Inject()(val messagesApi: MessagesApi, dbConfigProvider: DatabaseConfigProvider, mailerClient: MailerClient, configuration: Configuration) extends Controller with PermissionCheck {
  private implicit val db: Database = dbConfigProvider.get[JdbcProfile].db
  private implicit val mApi: MessagesApi = messagesApi
  private implicit val config: Configuration = configuration

  def index: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Users.sortBy(_.id).to[List].result).map { userList => Ok(org.ieee_passau.views.html.user.index(userList))}
  }}

  def edit(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Users.byId(id).result.headOption).flatMap  {
      case Some(user) => db.run(Permissions.to[List].result).map {
        permissions => Ok(org.ieee_passau.views.html.user.edit(id, userForm.fill(user), permissions))}
      case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }}

  def delete(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Users.filter(_.id === id).delete).map(_ =>
      Redirect(org.ieee_passau.controllers.routes.UserController.index())
    )
  }}

  def impersonate(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Users.byId(id).result.headOption).map {
      case Some(user) => Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
        .flashing("success" -> messagesApi("user.impersonate.message", user.username))
        .withSession("user" -> user.id.get.toString)
      case _ => NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def login: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action { implicit rs =>
    // if no post data, display empty form
    Ok(org.ieee_passau.views.html.user.login(loginForm))
  }}

  def authenticate: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action { implicit rs =>
    loginForm.bindFromRequest.fold(
      errorForm => {
        // Hack to clear the password field
        // set the old username
        val baseForm = loginForm.bind(Map("username" -> errorForm("username").value.getOrElse("")))
        val form = if (errorForm.hasGlobalErrors)
          // if global error -> login failed due to failed auth, clear the pass to short error
          baseForm.discardingErrors.withGlobalError(messagesApi("user.login.error"))
        else baseForm
        BadRequest(org.ieee_passau.views.html.user.login(form))
      },

      userLogin => {
        val user: User = userLogin.user.get
        val uid = user.id.get.toString
        Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
          .flashing("success" -> messagesApi("user.login.message", user.username))
          .withSession("user" -> uid)
          .withCookies(
            Cookie(play.Play.langCookieName(),
              user.lang.getOrElse(rs.acceptLanguages.headOption.getOrElse(play.api.i18n.Lang.defaultLang).language)
            )
          )
      }
    )
  }}

  def logout: Action[AnyContent] = requirePermission(Contestant) { implicit user => Action { implicit rs =>
    Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
      .flashing("success" -> messagesApi("user.logout.message", user.get.username))
      .withNewSession;
  }}

  def register: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action { implicit rs =>
    Ok(org.ieee_passau.views.html.user.register(registrationForm, config.getBoolean("captcha.active").getOrElse(false)))
  }}

  def create: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action.async { implicit rs =>
    registrationForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.user.register(errorForm, play.Configuration.root().getBoolean("captcha.active"))))
      },

      registration => {
        val createdUser: User = registration.makeUser(request2lang)
        val link = org.ieee_passau.controllers.routes.UserController.activate(createdUser.activationToken.get)
          .absoluteURL(secure = play.Configuration.root().getBoolean("application.https", false))
        val regMail = Email(
          subject = messagesApi("email.header") + " " + messagesApi("email.register.subject"),
          from = play.Configuration.root().getString("email.from"),
          to = List(createdUser.email),
          bodyText = Some(messagesApi("email.register.body", createdUser.username, link))
        )

        db.run((Users returning Users.map(_.id)) += createdUser).map { id =>
          try {
            mailerClient.send(regMail)

            Redirect(org.ieee_passau.controllers.routes.UserController.login())
              .flashing("success" -> messagesApi("user.register.error.existing", createdUser.username))
          } catch {
            case _: EmailException =>
              Users.filter(_.id === id).delete
              BadRequest(org.ieee_passau.views.html.user.register(
                registrationForm.fill(registration).withError("invalidEmail", "error.email"),
                play.Configuration.root().getBoolean("captcha.active"))
              )

            case e: Throwable =>
              Users.filter(_.id === id).delete
              throw e
          }
        }
      }
    )
  }}

  def activate(token: String): Action[AnyContent] = requirePermission(Guest) { _ => Action.async { implicit rs =>
    db.run(Users.byToken(token).result.headOption).map {
      case Some(user) =>
        implicit val sessionUser: User = user
        Users.update(user.id, user.copy(active = true, activationToken = None))
        Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
          .flashing("success" -> messagesApi("user.register.message", user.username))
          .addingToSession("user" -> user.id.get.toString)

      case None =>
        Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
          .flashing("warning" -> messagesApi("error.invalidlink"))
    }
  }}

  def resetPassword: Action[AnyContent] = requirePermission(Guest) { implicit guest =>  Action { implicit rs =>
    Ok(org.ieee_passau.views.html.user.requestPassword(usernameForm.fill("")))
  }}

  def requestPassword: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action.async { implicit rs =>
    usernameForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.user.requestPassword(errorForm)))
      },

      username => {
        db.run(Users.byUsername(username).result.headOption).map {
          case Some(user) =>
            val token = PasswordHasher.generateUrlString()
            val link = org.ieee_passau.controllers.routes.UserController.editPassword(token)
              .absoluteURL(secure = config.getBoolean("application.https").getOrElse(false))
            val regMail = Email(
              subject = messagesApi("email.header") + " " + messagesApi("email.passwordreset.subject"),
              from = config.getString("email.from").getOrElse(""),
              to = List(user.email),
              bodyText = Some(messagesApi("email.passwordreset.body", user.username, link))
            )
            mailerClient.send(regMail)
            Users.update(user.id, user.copy(activationToken = Some(token)))

            Redirect(org.ieee_passau.controllers.routes.UserController.login())
              .flashing("success" -> messagesApi("user.register.verify.message"))
          case _ => NotFound(org.ieee_passau.views.html.errors.e404())
        }
      }
    )
  }}

  def editPassword(token: String): Action[AnyContent] = requirePermission(Guest) { _ =>  Action.async { implicit rs =>
    db.run(Users.byToken(token).result.headOption).map { maybeUser =>
      implicit val sessionUser: Option[User] = maybeUser
      maybeUser match {
        case Some(user) =>
          Users.update(user.id, user.copy(active = true))
          Ok(org.ieee_passau.views.html.user.resetPassword(token, passwordForm.fill("")))
            .addingToSession("user" -> user.id.get.toString)

        case None =>
          Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
            .flashing("danger" -> messagesApi("error.invalidlink"))
      }
    }
  }}

  def updatePassword(token: String): Action[AnyContent] = requirePermission(Contestant) { implicit user => Action { implicit rs =>
    val sessionUser = user.get
    if (sessionUser.activationToken.getOrElse("") != token) {
      Unauthorized(org.ieee_passau.views.html.errors.e403())
    } else {

      passwordForm.bindFromRequest.fold(
        errorForm => {
          BadRequest(org.ieee_passau.views.html.user.resetPassword(token, errorForm))
        },

        password => {
          val pwh = PasswordHasher.hashPassword(password)
          db.run(Users.filter(_.id === sessionUser.id).map(_.lang).result.headOption).map { lang =>
            Users.update(sessionUser.id, sessionUser.copy(password = pwh, activationToken = None, lang = lang))
          }

          Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
            .flashing("success" -> messagesApi("user.login.message"))
        }
      )
    }
  }}

  def updateLang(lang: String): Action[AnyContent] = requirePermission(Everyone) { implicit user => Action { implicit rs =>
    val maybeLang = Lang.get(lang)
    if (maybeLang.isEmpty || !Lang.availables.contains(maybeLang.get)) {
      Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
        .flashing("danger" -> messagesApi("language.unsupported"))

    } else {
      if (user.nonEmpty) {
        Users.update(user.get.id, user.get.copy(lang = Some(lang)))
      }

      val refererUrl = rs.headers.get("referer")
      val cookie = Cookie(messagesApi.langCookieName, lang)
      if (refererUrl.nonEmpty) {
        Redirect(refererUrl.get, 303).withCookies(cookie)
      } else {
        Redirect(org.ieee_passau.controllers.routes.CmsController.calendar()).withCookies(cookie)
      }
    }
  }}

  def update(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    userForm.bindFromRequest.fold(
      errorForm => {
        db.run(Permissions.to[List].result).map { permissionList =>
          BadRequest(org.ieee_passau.views.html.user.edit(id, errorForm, permissionList))
        }
      },
      user => {
        db.run(Users.byId(id).result.headOption).map {
          case Some(dbUser) =>
            val password = if (user.password.isEmpty) dbUser.password else PasswordHasher.hashPassword(user.password)
            Users.update(dbUser.id, user.copy(password = password, activationToken = dbUser.activationToken, lang = dbUser.lang))
            Redirect(org.ieee_passau.controllers.routes.UserController.edit(id))
              .flashing("success" -> messagesApi("user.update.message", user.username))
          case _ => NotFound(org.ieee_passau.views.html.errors.e404())
        }
      }
    )
  }}

  def dismissNotification: Action[AnyContent] = requirePermission(Contestant) { implicit user => Action.async { implicit rs =>
    Users.update(user.get.id, user.get.copy(notificationDismissed = true)).map(_ => Ok(""))
  }}

  def userForm = Form(
    mapping(
      "id" -> optional(number),
      "username" -> nonEmptyText(3, 30),
      "password" -> optional(nonEmptyText(6, 128)),
      "email" -> email,
      "active" -> boolean,
      "hidden" -> boolean,
      "semester" -> optional(number),
      "studySubject" -> optional(nonEmptyText),
      "school" -> optional(nonEmptyText),
      "permission" -> nonEmptyText
    )
    ((id: Option[Int], username: String, password: Option[String], email: String, active: Boolean, hidden: Boolean,
      semester: Option[Int], studySubject: Option[String], school: Option[String], permission: String) =>
      User(id, username, password.getOrElse(""), email, active, hidden, semester, studySubject, school, None, None, Permission(permission), notificationDismissed = false))
    ((user: User) => Some(user.id, user.username, Some(""), user.email, user.active, user.hidden, user.semester, user.studySubject, user.school, user.permission.name))
  )

  def registrationForm = Form(
    mapping(
      "username" -> nonEmptyText(3, 30).verifying("user.error.usernametake", u => Users.usernameAvailable(u)),
      "password" -> tuple(
        "main" -> nonEmptyText(6, 128),
        "repeat" -> text
      ).verifying("user.error.passwordsnomatch", pw => pw._1 == pw._2),
      "email" -> email.verifying("user.error.emailtaken", e => Users.emailAvailable(e)),
      "semester" -> optional(number),
      "studySubject" -> optional(nonEmptyText),
      "school" -> optional(nonEmptyText),
      "g-recaptcha-response" -> text.verifying("user.error.captcha", value => CaptchaHelper.check(value))
    )
    ((username: String, password: (String, String), email: String, semester: Option[Int], studySubject: Option[String],
      school: Option[String], _: String ) => UserRegistration(username, password, email, semester, studySubject, school))
    ((user: UserRegistration) => Some(user.username, ("", ""), user.email, user.semester, user.studySubject, user.school, ""))

  )

  def loginForm = Form(
    mapping(
      "username" -> nonEmptyText(3, 30),
      "password" -> nonEmptyText(6, 128)
    )(UserLogin.apply)(UserLogin.unapply)
      verifying("user.login.error", login => login.authenticate().isDefined)
  )

  def passwordForm = Form(
    mapping(
      "password" -> tuple(
        "main" -> nonEmptyText(6, 128),
        "repeat" -> text
      ).verifying("user.error.passwordsnomatch", pw => pw._1 == pw._2)
    )((password: (String, String)) => password._1)((_: String) => Some("",""))
  )

  def usernameForm = Form(
    mapping(
      "username" -> nonEmptyText(3, 30)
    )((username: String) => username)((username: String) => Some(username))
      verifying("user.notexist", u => !Users.usernameAvailable(u))
  )
}
