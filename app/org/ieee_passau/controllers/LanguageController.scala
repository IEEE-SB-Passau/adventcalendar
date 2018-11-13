package org.ieee_passau.controllers

import com.google.inject.Inject
import org.ieee_passau.models._
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{mapping, of, _}
import play.api.data.format.Formats._
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class LanguageController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                   val components: MessagesControllerComponents,
                                   implicit val ec: ExecutionContext,
                                   implicit val config: Configuration
                                  ) extends MasterController(dbConfigProvider, components, ec, config) {

  def index: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Languages.sortBy(_.id.asc).to[List].result).map { list =>
      Ok(org.ieee_passau.views.html.language.index(list))
    }
  }}

  def list: Action[AnyContent] = requirePermission(Everyone) { implicit user => Action.async { implicit rs =>
    db.run(Languages.sortBy(_.id.asc).to[List].result).map { list =>
      Ok(org.ieee_passau.views.html.language.languages(list))
    }
  }}

  def edit(language: String): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    Languages.byLang(language).map {
      case Some(lang) => Ok(org.ieee_passau.views.html.language.edit(lang.id, languageUpdateForm.fill(lang)))
      case _ => NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def insert: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action { implicit rs =>
    Ok(org.ieee_passau.views.html.language.insert(languageNewForm))
  }}

  def save: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    languageNewForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.language.insert(errorForm)))
      },

      newCodelang => {
        db.run(Languages += newCodelang).map(_ =>
          Redirect(org.ieee_passau.controllers.routes.LanguageController.edit(newCodelang.id))
            .flashing("success" -> rs.messages("codelang.create.message", newCodelang.id))
        )
      }
    )
  }}

  def update(language: String): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    Languages.byLang(language).flatMap {
      case Some(lang) => languageUpdateForm.bindFromRequest.fold(
        errorForm => {
          Future.successful(BadRequest(org.ieee_passau.views.html.language.edit(lang.id, errorForm)))
        },

        codelang => {
          val newCodelang = lang.copy(name = codelang.name, cpuFactor = codelang.cpuFactor, memFactor = codelang.memFactor, comment = codelang.comment)
          db.run(Languages.update(language, newCodelang)).map(_ =>
            Redirect(org.ieee_passau.controllers.routes.LanguageController.edit(language))
              .flashing("success" -> rs.messages("codelang.update.message", codelang.id))
          )
        }
      )
      case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }}

  val languageNewForm = Form(
    mapping(
      "id" -> text,
      "name" -> text,
      "highlightClass" -> text,
      "extension" -> text,
      "cpuFactor" -> of[Float],
      "memFactor" -> of[Float],
      "comment" -> text
    )(Language.apply)(Language.unapply)
  )

  val languageUpdateForm = Form(
    mapping(
      "name" -> text,
      "cpuFactor" -> of[Float],
      "memFactor" -> of[Float],
      "comment" -> text
    )((name, cpuFactor, memFactor, comment) => Language("", name, "", "", cpuFactor, memFactor, comment))
    ((l: Language) => Some((l.name, l.cpuFactor, l.memFactor, l.comment)))
  )
}
