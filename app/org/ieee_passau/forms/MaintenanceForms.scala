package org.ieee_passau.forms

import java.util.Date

import org.ieee_passau.models.Posting
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Lang

object MaintenanceForms {
  val statusForm = Form(
    mapping(
      "state" -> text
    )((state: String) => state == "true")((status: (Boolean)) => Some(status.toString))
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
