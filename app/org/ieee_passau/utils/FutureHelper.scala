package org.ieee_passau.utils

import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object FutureHelper {
  val dbTimeout: FiniteDuration = 2 seconds
  implicit val akkaTimeout: Timeout = Timeout(2 seconds)

  def makeDuration(t: String): FiniteDuration = {
    val dur = Duration(t)
    FiniteDuration(dur.toMillis, "milliseconds")
  }
}
