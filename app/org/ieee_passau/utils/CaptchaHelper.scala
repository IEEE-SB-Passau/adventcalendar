package org.ieee_passau.utils

import com.google.inject.Inject
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

class CaptchaHelper @Inject() (ws: WSClient) {
  def check(response: String)(implicit config: Configuration, ex: ExecutionContext): Boolean = {
    if (!config.getOptional[Boolean]("captcha.active").getOrElse(false)) return true
    Await.result(ws.url("https://www.google.com/recaptcha/api/siteverify").post(Map(
      "secret" -> Seq(config.getOptional[String]("captcha.secret").getOrElse("")),
      "response" -> Seq(response)
    )).map {
      response => (response.json \ "success").as[Boolean]
    }, FutureHelper.dbTimeout)
  }
}
