package org.ieee_passau.forms

import org.ieee_passau.models._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages

object UserForms {
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
      User(id, username, password.getOrElse(""), email, active, hidden, semester, studySubject, school, None, None, Permission(permission)))
    ((user: User) => Some(user.id, user.username, Some(""), user.email, user.active, user.hidden, user.semester, user.studySubject, user.school, user.permission.name))
  )

  def registrationForm = Form(
    mapping(
      "username" -> nonEmptyText(3, 30).verifying(Messages("user.error.usernametake"), u => Users.usernameAvailable(u)),
      "password" -> tuple(
        "main" -> nonEmptyText(6, 128),
        "repeat" -> text
      ).verifying(Messages("user.error.passwordsnomatch"), pw => pw._1 == pw._2),
      "email" -> email.verifying(Messages("user.error.emailtaken"), e => Users.emailAvailable(e)),
      "semester" -> optional(number),
      "studySubject" -> optional(nonEmptyText),
      "school" -> optional(nonEmptyText)
    )(UserRegistration.apply)(UserRegistration.unapply)
  )

  def loginForm = Form(
    mapping(
      "username" -> nonEmptyText(3, 30),
      "password" -> nonEmptyText(6, 128)
    )(UserLogin.apply)(UserLogin.unapply)
      verifying(Messages("user.login.error"), login => login.authenticate().isDefined)
  )

  def passwordForm = Form(
    mapping(
      "password" -> tuple(
        "main" -> nonEmptyText(6, 128),
        "repeat" -> text
      ).verifying(Messages("user.error.passwordsnomatch"), pw => pw._1 == pw._2)
    )((password: (String, String)) => password._1)((_: String) => Some("",""))
  )

  def usernameForm = Form(
    mapping(
      "username" -> nonEmptyText(3, 30)
    )((username: String) => username)((username: String) => Some(username))
      verifying(Messages("user.notexist"), u => !Users.usernameAvailable(u))
  )

  def feedbackForm = Form(
    mapping(
      "id" -> optional(number),
      "user_id" -> number,
      "rating" -> number(1, 5),
      "pro" -> optional(text),
      "con" -> optional(text),
      "freetext" -> optional(text)
    )(Feedback.apply)(Feedback.unapply)
  )
}
