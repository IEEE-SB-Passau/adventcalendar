package org.ieee_passau

import com.google.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.HttpFilters
import play.api.i18n.MessagesApi
import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFFilter

class Filters @Inject()(dbConfigProvider: DatabaseConfigProvider, val messagesApi: MessagesApi) extends HttpFilters {
  val csrfFilter: CSRFFilter = CSRFFilter(errorHandler = new CSRFFilterErrorHandler(dbConfigProvider, messagesApi))

  override def filters: Seq[EssentialFilter] = Seq(csrfFilter)
}