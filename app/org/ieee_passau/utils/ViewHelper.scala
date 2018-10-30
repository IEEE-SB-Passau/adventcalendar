package org.ieee_passau.utils

import org.ieee_passau.models._
import play.api.data.Form

import scala.xml.Elem

object ViewHelper {

  val NoHighlight = 0
  val Highlight = 1
  val HighlightSpecial = 2

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

  def checkAmbiguesKey(prefix: String, form: Form[_]): (Boolean, String) = {
    var hasError = false
    var error = ""
    if(form.errors.nonEmpty) {
      form.errors.foreach { e => if(e.messages.head.contains(prefix)) {
        hasError = true
        error = e.message.substring(prefix.length)
      }}
    }
    (hasError, error)
  }

  def isErrorResult(result: Result): Boolean = {
    result match {
      case ProgramError | CompileError | MemoryExceeded | RuntimeExceeded | WrongAnswer => true
      case _ => false
    }
  }
}
