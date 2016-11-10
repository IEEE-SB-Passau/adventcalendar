package org.ieee_passau.utils

object CausedBy {
  def unapply(e: Throwable): Option[Throwable] = Option(e.getCause)
}
