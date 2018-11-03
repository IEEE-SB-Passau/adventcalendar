package org.ieee_passau.controllers

import com.google.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.I18nSupport
import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

class ControllerWithDBAndI18n @Inject()(dbConfigProvider: DatabaseConfigProvider, components: MessagesControllerComponents) extends MessagesAbstractController(components) with I18nSupport {
  implicit val db: Database = dbConfigProvider.get[JdbcProfile].db
}
