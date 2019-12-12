package org.ieee_passau.models

import org.ieee_passau.utils.LanguageHelper.LangTypeMapper
import org.ieee_passau.utils.{FutureHelper, LanguageHelper, PasswordHasher}
import play.api.i18n.Lang
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{CompiledFunction, ProvenShape}
import slick.sql.FixedSqlAction

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

case class User(id: Option[Int], username: String, password: String, email: String, active: Boolean, hidden: Boolean,
                lang: Lang, activationToken: Option[String], permission: Permission, notificationDismissed: Boolean
               ) extends Entity[User] {
  override def withId(id: Int): User = this.copy(id = Some(id))
}

class Users(tag: Tag) extends TableWithId[User](tag, "users") {
  def username: Rep[String] = column[String]("username")
  def password: Rep[String] = column[String]("password")
  def email: Rep[String] = column[String]("email")
  def active: Rep[Boolean] = column[Boolean]("is_active")
  def hidden: Rep[Boolean] = column[Boolean]("is_hidden")
  def activationToken: Rep[String] = column[String]("token")
  def lang: Rep[Lang] = column[Lang]("language")(LanguageHelper.LangTypeMapper)
  def permission: Rep[Permission] = column[Permission]("permission")(Permission.permissionTypeMapper)
  def notificationDismissed: Rep[Boolean] = column[Boolean]("notification_dismissed")

  override def * : ProvenShape[User] = (id.?, username, password, email, active, hidden, lang.?, activationToken.?,
    permission, notificationDismissed) <> (rowToUser, userToRow)

  private val rowToUser: ((Option[Int], String, String, String, Boolean, Boolean, Option[Lang], Option[String], Permission, Boolean)) => User = {
    case (id: Option[Int], username: String, password: String, email: String, active: Boolean, hidden: Boolean,
          lang: Option[Lang], activationToken: Option[String], permission: Permission, notificationDismissed: Boolean) =>
      User(id, username, password, email, active, hidden, lang.getOrElse(LanguageHelper.defaultLanguage),
           activationToken, permission, notificationDismissed)
  }

  private val userToRow: User => Option[(Option[Int], String, String, String, Boolean, Boolean, Option[Lang], Option[String], Permission, Boolean)] = {
    case User(id: Option[Int], username: String, password: String, email: String, active: Boolean, hidden: Boolean,
              lang: Lang, activationToken: Option[String], permission: Permission, notificationDismissed: Boolean) =>
      Some((id, username, password, email, active, hidden, Some(lang), activationToken, permission, notificationDismissed))
  }
}

object Users extends TableQuery(new Users(_)) {
  def byUsername: CompiledFunction[Rep[String] => Query[Users, User, Seq], Rep[String], String, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.username)
  def byId: CompiledFunction[Rep[Int] => Query[Users, User, Seq], Rep[Int], Int, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.id)
  def byToken: CompiledFunction[Rep[String] => Query[Users, User, Seq], Rep[String], String, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.activationToken)

  def update(id: Option[Int], user: User): FixedSqlAction[Int, NoStream, Effect.Write] =
    this.byId(id.get).update(user.withId(id.get))

  def usernameAvailable(username: String)(implicit db: Database): Boolean =
    Await.result(db.run(Query(Users.filter(_.username === username).length).result), FutureHelper.dbTimeout).head == 0
  def emailAvailable(email: String)(implicit db: Database): Boolean =
    Await.result(db.run(Query(Users.filter(_.email === email).length).result), FutureHelper.dbTimeout).head == 0
}

case class UserLogin(username: String, password: String, stayLoggedIn: Boolean) {
  private var _user: Option[User] = None
  def user: Option[User] = _user

  def authenticate()(implicit db: Database, ec: ExecutionContext): Option[User] = {
    _user = None
    Await.result(db.run(Users.filter(_.username === this.username).result.headOption).map {
      case Some(user) if user.active && PasswordHasher.verifyPassword(this.password, user.password) =>
        _user = Some(user)
      case _ =>
        _user = None
    }, FutureHelper.dbTimeout)
    _user
  }
}

case class UserRegistration(username: String, password: (String, String), email: String, school: Option[String]) {
  def makeUser(lang: Lang): User = {
    val pwd = PasswordHasher.hashPassword(this.password._1)
    val link = PasswordHasher.generateUrlString()

    User(
      id = None,
      username = this.username,
      password = pwd,
      email = this.email,
      active = false,
      hidden = false,
      lang = lang,
      activationToken = Some(link),
      permission = Contestant,
      notificationDismissed= false
    )
  }
}

case class School(name: Option[String])
class Schools(tag: Tag) extends Table[School](tag, "schools") {
  def school: Rep[Option[String]] = column[Option[String]]("school")
  def * : ProvenShape[School] = school <> (School.apply, School.unapply)
}
object Schools extends TableQuery(new Schools(_))

