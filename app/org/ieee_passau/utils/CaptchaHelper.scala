package org.ieee_passau.utils

import play.api.Play.current
import play.api.libs.ws.WS

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object CaptchaHelper {
  def check(response: String): Boolean = {
    implicit val context = play.api.libs.concurrent.Execution.Implicits.defaultContext
    if (!play.Configuration.root().getBoolean("captcha.active")) return true
    Await.result(WS.url("https://www.google.com/recaptcha/api/siteverify").post(Map(
      "secret" -> Seq(play.Configuration.root().getString("captcha.secret")),
      "response" -> Seq(response)
    )).map {
      response => (response.json \ "success").as[Boolean]
    }, 2 seconds)
  }
}
