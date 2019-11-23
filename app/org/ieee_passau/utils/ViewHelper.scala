package org.ieee_passau.utils

import org.ieee_passau.models._
import play.api.data.Form

import scala.xml.Elem

object ViewHelper {

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

  def checkAmbiguousKey(suffix: String, form: Form[_]): (Boolean, String) = {
    var hasError = false
    var error = ""
    if(form.errors.nonEmpty) {
      form.errors.foreach { e => if(e.messages.head.endsWith(suffix)) {
        hasError = true
        error = e.message
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
