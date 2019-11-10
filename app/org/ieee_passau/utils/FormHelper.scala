package org.ieee_passau.utils

import org.ieee_passau.controllers.Beans.TicketText
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, nonEmptyText}

object FormHelper {

  val ticketForm: Form[TicketText] = Form(
    mapping(
      "text" -> nonEmptyText,
      "public" -> boolean
    )(TicketText.apply)(TicketText.unapply)
  )
}
