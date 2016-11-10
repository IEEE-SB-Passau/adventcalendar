package org.ieee_passau.utils

import org.ieee_passau.models.{User, Users}
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._

import scala.xml.Elem

object ViewHelper {

  def getUser(session: play.api.mvc.Session): Option[User] = {
    val maybeUid = session.get("user")
    if (maybeUid.isEmpty) {
      return None
    }

    val uid = maybeUid.get.toInt

    DB.withSession[Option[User]] { implicit s: play.api.db.slick.Session =>
      Users.byId(uid).firstOption;
    }
  }

  /**
    * Creates HTML to show a boolean value with a icon
    *
    * @param value a boolean
    * @return html to show a boolean value with a icon
    */
  def showCheckmark(value: Boolean): Elem = {
    if (value) {
      <span class="glyphicon glyphicon-ok"></span>
    } else {
      <span class="glyphicon glyphicon-remove"></span>
    }
  }
}