package org.ieee_passau

import com.google.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.DefaultHttpFilters
import play.api.i18n.MessagesApi
import play.filters.csrf.CSRFFilter

class Filters @Inject()(dbConfigProvider: DatabaseConfigProvider, messagesApi: MessagesApi, csrfFilter: CSRFFilter) extends DefaultHttpFilters(csrfFilter)