package org.ieee_passau.utils

import play.api.Configuration
import play.api.Play.current
import play.api.libs.ws.WS

import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

object CaptchaHelper {
  def check(response: String)(implicit config: Configuration, ex: ExecutionContext): Boolean = {
    if (!config.getBoolean("captcha.active").getOrElse(false)) return true
    Await.result(WS.url("https://www.google.com/recaptcha/api/siteverify").post(Map(
      "secret" -> Seq(config.getString("captcha.secret").getOrElse("")),
      "response" -> Seq(response)
    )).map {
      response => (response.json \ "success").as[Boolean]
    }, FutureHelper.dbTimeout)
  }
}
