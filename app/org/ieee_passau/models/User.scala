package org.ieee_passau.models

import org.ieee_passau.utils.{FutureHelper, PasswordHasher}
import play.api.i18n.Lang
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{CompiledFunction, ProvenShape}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

case class User(id: Option[Int], username: String, password: String, email: String, active: Boolean, hidden: Boolean,
                semester: Option[Int], studySubject: Option[String], school: Option[String],
                lang: Option[String], activationToken: Option[String], permission: Permission,  notificationDismissed: Boolean) extends Entity[User] {
  override def withId(id: Int): User = this.copy(id = Some(id))
}

class Users(tag: Tag) extends TableWithId[User](tag, "users") {
  def username: Rep[String] = column[String]("username")
  def password: Rep[String] = column[String]("password")
  def email: Rep[String] = column[String]("email")
  def active: Rep[Boolean] = column[Boolean]("is_active")
  def hidden: Rep[Boolean] = column[Boolean]("is_hidden")
  def semester: Rep[Int] = column[Int]("semester")
  def school: Rep[String] = column[String]("school")
  def studySubject: Rep[String] = column[String]("study_subject")
  def activationToken: Rep[String] = column[String]("token")
  def lang: Rep[String] = column[String]("language")
  def permission: Rep[Permission] = column[Permission]("permission")(Permission.permissionTypeMapper)
  def notificationDismissed: Rep[Boolean] = column[Boolean]("notification_dismissed")

  override def * : ProvenShape[User] = (id.?, username, password, email, active, hidden, semester.?,
    studySubject.?, school.?, lang.?, activationToken.?, permission, notificationDismissed) <> (User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_)) {
  def byUsername: CompiledFunction[Rep[String] => Query[Users, User, Seq], Rep[String], String, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.username)
  def byId: CompiledFunction[Rep[Int] => Query[Users, User, Seq], Rep[Int], Int, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.id)
  def byToken: CompiledFunction[Rep[String] => Query[Users, User, Seq], Rep[String], String, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.activationToken)

  def update(id: Option[Int], user: User)(implicit db: Database): Future[Int] =
    db.run(this.byId(id.get).update(user.withId(id.get)))

  def usernameAvailable(username: String)(implicit db: Database): Boolean =
    Await.result(db.run(Query(Users.filter(_.username === username).length).result), FutureHelper.dbTimeout).head == 0
  def emailAvailable(email: String)(implicit db: Database): Boolean =
    Await.result(db.run(Query(Users.filter(_.email === email).length).result), FutureHelper.dbTimeout).head == 0
}

case class UserLogin(username: String, password: String) {
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

case class UserRegistration(username: String, password: (String, String), email: String, semester: Option[Int],
                            studySubject: Option[String], school: Option[String]) {
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
      semester = this.semester,
      studySubject = this.studySubject,
      school = this.school,
      lang = Some(lang.code),
      activationToken = Some(link),
      permission = Contestant,
      notificationDismissed= false
    )
  }
}
