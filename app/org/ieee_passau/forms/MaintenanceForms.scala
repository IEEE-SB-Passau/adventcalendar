package org.ieee_passau.forms

import java.util.Date

import org.ieee_passau.models.Posting
import play.api.data.Forms._
import play.api.data._

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
      "title" -> text,
      "content" -> text
    )((id, title, content) => Posting(id, title, content, new Date))((p: Posting) => Some((p.id, p.title, p.content)))
  )
}