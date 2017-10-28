package org.ieee_passau.utils

import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, FiniteDuration}

object MathHelper {
  def max[T](list: List[T])(implicit ord: Ordering[T]): T = {
    if (list.isEmpty)
      throw new RuntimeException("maximum of empty list")

    @tailrec
    def inner(list: List[T], currMax: T): T =
      list match {
        case Nil => currMax
        case head :: tail => inner(tail, ord.max(head, currMax))
      }

    inner(list.tail, list.head)
  }

  def makeDuration(t: String): FiniteDuration = {
    val dur = Duration(t)
    FiniteDuration(dur.toMillis, "milliseconds")
  }
}
