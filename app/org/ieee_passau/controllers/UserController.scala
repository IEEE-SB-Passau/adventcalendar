package org.ieee_passau.controllers

import com.google.inject.Inject
import org.apache.commons.mail.EmailException
import org.ieee_passau.models.{Admin, _}
import org.ieee_passau.utils.{CaptchaHelper, LanguageHelper, PasswordHasher}
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.HttpEntity
import play.api.i18n.{Lang, Langs}
import play.api.libs.mailer._
import play.api.mvc.{Result, Session, _}
import play.api.{Configuration, Environment}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class UserController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                               val components: MessagesControllerComponents,
                               val langs: Langs,
                               val captchaHelper: CaptchaHelper,
                               val mailerClient: MailerClient,
                               implicit val ec: ExecutionContext,
                               implicit val config: Configuration,
                               val env: Environment
                              ) extends MasterController(dbConfigProvider, components, ec, config, env) {

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
        .flashing("success" -> rs.messages("user.impersonate.message", user.username))
        .withSession("user" -> user.id.get.toString)
      case _ => NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def login: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action { implicit rs =>
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
          baseForm.discardingErrors.withGlobalError(rs.messages("user.login.error"))
        else baseForm
        BadRequest(org.ieee_passau.views.html.user.login(form))
      },

      userLogin => {
        val user: User = userLogin.user.get
        val uid = user.id.get.toString
        var intermediateResult = Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
          .flashing("success" -> rs.messages("user.login.message", user.username))
          .withSession("user" -> uid, "stayLoggedIn" -> userLogin.stayLoggedIn.toString)
          .withCookies(generateLangCookie(user.lang.code, userLogin.stayLoggedIn))
        if (userLogin.stayLoggedIn) {
          intermediateResult = new ResultWithPersistentSessionCookie(intermediateResult)
        }
        intermediateResult
      }
    )
  }}

  private def generateLangCookie(lang: String, persistent: Boolean)(implicit config: Configuration): Cookie = {
    if (persistent) {
      val maxAge = config.get[Option[FiniteDuration]]("persistentSession.maxAge").map(_.toSeconds.toInt)
      Cookie(messagesApi.langCookieName, lang, maxAge = maxAge)
    } else {
      Cookie(messagesApi.langCookieName, lang)
    }
  }

  def logout: Action[AnyContent] = requirePermission(Contestant) { implicit user => Action { implicit rs =>
    Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
      .flashing("success" -> rs.messages("user.logout.message", user.get.username)).withNewSession;
  }}

  def register: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action { implicit rs =>
    Ok(org.ieee_passau.views.html.user.register(registrationForm, config.getOptional[Boolean]("captcha.active").getOrElse(false)))
  }}

  def create: Action[AnyContent] = requirePermission(Guest) { implicit guest => Action.async { implicit rs =>
    registrationForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.user.register(errorForm, config.getOptional[Boolean]("captcha.active").getOrElse(false))))
      },

      registration => {
        implicit val sessionLang: Lang = rs.lang
        val createdUser: User = registration.makeUser(sessionLang)
        val link = org.ieee_passau.controllers.routes.UserController.activate(createdUser.activationToken.get)
          .absoluteURL(secure = config.getOptional[Boolean]("application.https").getOrElse(false))
        val regMail = Email(
          subject = messagesApi("email.header") + " " + messagesApi("email.register.subject"),
          from = config.getOptional[String]("email.from").getOrElse("adventskalender@ieee.uni-passau.de"),
          to = List(createdUser.email),
          bodyText = Some(messagesApi("email.register.body", createdUser.username, link))
        )

        db.run((Users returning Users.map(_.id)) += createdUser).map { id =>
          try {
            mailerClient.send(regMail)

            registration.school.map(school => db.run(Schools += School(Some(school))))

            Redirect(org.ieee_passau.controllers.routes.UserController.login())
              .flashing("success" -> messagesApi("user.register.error.existing", createdUser.username))
          } catch {
            case _: EmailException =>
              Users.filter(_.id === id).delete
              BadRequest(org.ieee_passau.views.html.user.register(
                registrationForm.fill(registration).withError("invalidEmail", "error.email"),
                config.getOptional[Boolean]("captcha.active").getOrElse(false))
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
        db.run(Users.update(user.id, user.copy(active = true, activationToken = None)))
        Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
          .flashing("success" -> rs.messages("user.register.message", user.username))
          .addingToSession("user" -> user.id.get.toString)
      case None =>
        Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
          .flashing("warning" -> rs.messages("error.invalidlink"))
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
          case Some(user) if user.permission != Internal =>
            val token = PasswordHasher.generateUrlString()
            val link = org.ieee_passau.controllers.routes.UserController.editPassword(token)
              .absoluteURL(secure = config.getOptional[Boolean]("application.https").getOrElse(false))
            val regMail = Email(
              subject = rs.messages("email.header") + " " + rs.messages("email.passwordreset.subject"),
              from = config.getOptional[String]("email.from").getOrElse(""),
              to = List(user.email),
              bodyText = Some(rs.messages("email.passwordreset.body", user.username, link))
            )
            mailerClient.send(regMail)
            db.run(Users.update(user.id, user.copy(activationToken = Some(token))))

            Redirect(org.ieee_passau.controllers.routes.UserController.login())
              .flashing("success" -> rs.messages("user.register.verify.message"))
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
          db.run(Users.update(user.id, user.copy(active = true)))
          Ok(org.ieee_passau.views.html.user.resetPassword(token, passwordForm.fill("")))
            .addingToSession("user" -> user.id.get.toString)

        case None =>
          Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
            .flashing("danger" -> rs.messages("error.invalidlink"))
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
          db.run(Users.update(sessionUser.id, sessionUser.copy(password = pwh, activationToken = None)))

          Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
            .flashing("success" -> rs.messages("user.login.message", sessionUser.username))
        }
      )
    }
  }}

  def updateLang(lang: String): Action[AnyContent] = requirePermission(Everyone) { implicit user => Action { implicit rs =>
    val maybeLang = Lang.get(lang)
    if (maybeLang.isEmpty || !langs.availables.contains(maybeLang.get)) {
      Redirect(org.ieee_passau.controllers.routes.CmsController.calendar())
        .flashing("danger" -> rs.messages("language.unsupported"))
    } else {
      if (user.nonEmpty) {
        db.run(Users.update(user.get.id, user.get.copy(lang = maybeLang.get)))
      }

      val refererUrl = rs.headers.get("referer")
      val langCookie = generateLangCookie(lang, rs.session.get("stayLoggedIn").contains("true"))
      if (refererUrl.nonEmpty) {
        Redirect(refererUrl.get, 303).withCookies(langCookie)
      } else {
        Redirect(org.ieee_passau.controllers.routes.CmsController.calendar()).withCookies(langCookie)
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
            db.run(Users.update(dbUser.id, user.copy(password = password, activationToken = dbUser.activationToken, lang = dbUser.lang)))
            Redirect(org.ieee_passau.controllers.routes.UserController.edit(id))
              .flashing("success" -> rs.messages("user.update.message", user.username))
          case _ => NotFound(org.ieee_passau.views.html.errors.e404())
        }
      }
    )
  }}

  def dismissNotification: Action[AnyContent] = requirePermission(Contestant) { implicit user => Action.async { implicit rs =>
    db.run(Users.update(user.get.id, user.get.copy(notificationDismissed = true)).map(_ => Ok("")))
  }}

  // hack, that allows us to programmatically set the maxAge for the session-cookie (playframework doesn't have that functionality)
  class ResultWithPersistentSessionCookie(
      header: ResponseHeader,
      body: HttpEntity,
      newSession: Option[Session] = None,
      newFlash: Option[Flash] = None,
      newCookies: Seq[Cookie] = Seq.empty
  ) extends Result(header, body, newSession, newFlash, newCookies) {

    def this(result: Result) = this(result.header, result.body, result.newSession, result.newFlash, result.newCookies)

    // override copy, since it is used in the Result class, but we want that this class will be returned
    override def copy(
        header: ResponseHeader = this.header,
        body: HttpEntity = this.body,
        newSession: Option[Session] = this.newSession,
        newFlash: Option[Flash] = this.newFlash,
        newCookies: Seq[Cookie] = this.newCookies
    ): ResultWithPersistentSessionCookie = new ResultWithPersistentSessionCookie(header, body, newSession, newFlash, newCookies)

    override def bakeCookies(
        cookieHeaderEncoding: CookieHeaderEncoding = new DefaultCookieHeaderEncoding(),
        sessionBaker: CookieBaker[Session] = new DefaultSessionCookieBaker(),
        flashBaker: CookieBaker[Flash] = new DefaultFlashCookieBaker(),
        requestHasFlash: Boolean = false
    ): ResultWithPersistentSessionCookie = {
      val maxAge = config.get[Option[FiniteDuration]]("persistentSession.maxAge")

      val actualSessionBaker: CookieBaker[Session] = sessionBaker match {
        case defaultSessionBaker: DefaultSessionCookieBaker =>
          val extendedConfig = defaultSessionBaker.config.copy(maxAge = maxAge)
          new DefaultSessionCookieBaker(extendedConfig, defaultSessionBaker.secretConfiguration, defaultSessionBaker.signedCodec.cookieSigner)
        case legacySessionCookieBaker: LegacySessionCookieBaker =>
          val extendedConfig = legacySessionCookieBaker.config.copy(maxAge = maxAge)
          new LegacySessionCookieBaker(extendedConfig, legacySessionCookieBaker.cookieSigner)
        case _ =>
          log.error("Unknown SessionBaker is being used: " + sessionBaker.getClass.getName)
          sessionBaker
      }

      new ResultWithPersistentSessionCookie(super.bakeCookies(cookieHeaderEncoding, actualSessionBaker, flashBaker, requestHasFlash))
    }
  }

  val userForm: Form[User] = Form(
    mapping(
      "id" -> optional(number),
      "username" -> nonEmptyText(3, 30),
      "password" -> optional(nonEmptyText(6, 128)),
      "email" -> email,
      "active" -> boolean,
      "hidden" -> boolean,
      "permission" -> nonEmptyText
    )
    ((id: Option[Int], username: String, password: Option[String], email: String, active: Boolean, hidden: Boolean, permission: String) =>
      User(id, username, password.getOrElse(""), email, active, hidden, LanguageHelper.defaultLanguage, None,
        Permission(permission), notificationDismissed = false))
    ((user: User) => Some(user.id, user.username, Some(""), user.email, user.active, user.hidden, user.permission.name))
  )

  val registrationForm: Form[UserRegistration] = Form(
    mapping(
      "username" -> nonEmptyText(3, 30).verifying("user.error.usernametake", u => Users.usernameAvailable(u)),
      "password" -> tuple(
        "main" -> nonEmptyText(6, 128),
        "repeat" -> text
      ).verifying("user.error.passwordsnomatch", pw => pw._1 == pw._2),
      "email" -> email.verifying("user.error.emailtaken", e => Users.emailAvailable(e)),
      "school" -> optional(nonEmptyText),
      "g-recaptcha-response" -> text.verifying("user.error.captcha", value => captchaHelper.check(value))
    )
    ((username: String, password: (String, String), email: String, school: Option[String], _: String ) =>
      UserRegistration(username, password, email, school))
    ((user: UserRegistration) => Some(user.username, ("", ""), user.email, user.school, ""))

  )

  val loginForm: Form[UserLogin] = Form(
    mapping(
      "username" -> nonEmptyText(3, 30),
      "password" -> nonEmptyText(6, 128),
      "stayLoggedIn" -> boolean
    )(UserLogin.apply)(UserLogin.unapply)
      verifying("user.login.error", login => login.authenticate().isDefined)
  )

  val passwordForm: Form[String] = Form(
    mapping(
      "password" -> tuple(
        "main" -> nonEmptyText(6, 128),
        "repeat" -> text
      ).verifying("user.error.passwordsnomatch", pw => pw._1 == pw._2)
    )((password: (String, String)) => password._1)((_: String) => Some("",""))
  )

  val usernameForm: Form[String] = Form(
    mapping(
      "username" -> nonEmptyText(3, 30)
    )((username: String) => username)((username: String) => Some(username))
      verifying("user.notexist", u => !Users.usernameAvailable(u))
  )
}
