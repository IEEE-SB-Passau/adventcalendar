package org.ieee_passau.forms

import java.util.Date

import org.ieee_passau.models.Posting
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Lang

object MaintenanceForms {
  val statusForm = Form(
    mapping(
      "state" -> text,
      "msg" -> text
    )((state: String, message: String) => (state == "true", message))((status: (Boolean, String)) => Some((status._1.toString, status._2)))
  )

  val postingForm = Form(
    mapping(
      "id" -> optional(number),
      "lang" -> text,
      "title" -> text,
      "content" -> text
    )((id, lang, title, content) => Posting(id, Lang(lang), title, content, new Date))
    ((p: Posting) => Some((p.id, p.lang.code, p.title, p.content)))
  )
}