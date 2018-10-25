package org.ieee_passau.models

import org.ieee_passau.utils.PasswordHasher
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{Session => _, _}
import play.api.i18n.Lang

import scala.slick.lifted.{CompiledFunction, ProvenShape}

case class User(id: Option[Int], username: String, password: String, email: String, active: Boolean, hidden: Boolean,
                semester: Option[Int], studySubject: Option[String], school: Option[String],
                lang: Option[String], activationToken: Option[String], permission: Permission) extends Entity[User] {
  override def withId(id: Int): User = this.copy(id = Some(id))
}

class Users(tag: Tag) extends TableWithId[User](tag, "users") {
  def username: Column[String] = column[String]("username")
  def password: Column[String] = column[String]("password")
  def email: Column[String] = column[String]("email")
  def active: Column[Boolean] = column[Boolean]("is_active")
  def hidden: Column[Boolean] = column[Boolean]("is_hidden")
  def semester: Column[Int] = column[Int]("semester")
  def school: Column[String] = column[String]("school")
  def studySubject: Column[String] = column[String]("study_subject")
  def activationToken: Column[String] = column[String]("token")
  def lang: Column[String] = column[String]("language")
  def permission: Column[Permission] = column[Permission]("permission") (Permission.permissionTypeMapper)

  override def * : ProvenShape[User] = (id.?, username, password, email, active, hidden, semester.?,
    studySubject.?, school.?, lang.?, activationToken.?, permission) <> (User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_)) {
  def byUsername: CompiledFunction[Column[String] => Query[Users, User, Seq], Column[String], String, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.username)
  def byId: CompiledFunction[Column[Int] => Query[Users, User, Seq], Column[Int], Int, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.id)
  def byToken: CompiledFunction[Column[String] => Query[Users, User, Seq], Column[String], String, Query[Users, User, Seq], Seq[User]] =
    this.findBy(_.activationToken)
  def update(id: Int, user: User)(implicit session: Session): Int =
    this.filter(_.id === id).update(user.withId(id))

  def usernameAvailable(username: String): Boolean = {
    DB.withSession { implicit session: Session =>
      Query(Users.filter(_.username === username).length).first == 0
    }
  }

  def emailAvailable(email: String): Boolean = {
    DB.withSession { implicit session: Session =>
      Query(Users.filter(_.email === email).length).first == 0
    }
  }
}

case class UserLogin(username: String, password: String) {

  private var _user: Option[User] = None
  def user: Option[User] = _user

  def authenticate(): Option[User] = {
    DB.withSession { implicit s: Session =>
      this._user = None

      val maybeUser = Users.filter(_.username === this.username).firstOption

      if (maybeUser.isEmpty) {
        return None
      }

      val user = maybeUser.get
      if (!user.active) {
        return None
      }
      if (!PasswordHasher.verifyPassword(this.password, user.password)) {
        return None
      }

      this._user = Some(user)
      return Some(user);
    }
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
      permission = Contestant
    )
  }
}
