package org.ieee_passau.controllers

import com.google.inject.Inject
import org.ieee_passau.models._
import play.api.data.Form
import play.api.data.Forms.{mapping, of, _}
import play.api.data.format.Formats._
import play.api.data.validation.Constraints.pattern
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import play.api.{Configuration, Environment}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class LanguageController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                   val components: MessagesControllerComponents,
                                   implicit val ec: ExecutionContext,
                                   implicit val config: Configuration,
                                   val env: Environment
                                  ) extends MasterController(dbConfigProvider, components, ec, config, env) {

  def index: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Languages.sortBy(_.id.asc).to[List].result).map { list =>
      Ok(org.ieee_passau.views.html.language.index(list))
    }
  }}

  def list: Action[AnyContent] = requirePermission(Everyone) { implicit user => Action.async { implicit rs =>
    db.run(Languages.filter(_.active).sortBy(_.id.asc).to[List].result).map { list =>
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
    Ok(org.ieee_passau.views.html.language.insert(languageNewForm.bind(Map("active" -> "true")).discardingErrors))
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
      case Some(lang) =>
        // If the id was changed, check if it is available.
        var filledForm = languageUpdateForm.bindFromRequest()
        val idValue = filledForm.apply("id").value.get
        if (idValue != language && !Languages.idAvailable(idValue)) {
          filledForm = filledForm.withError("id", "codelang.id.error.taken")
        }
        filledForm.fold(
          errorForm => {
            Future.successful(BadRequest(org.ieee_passau.views.html.language.edit(lang.id, errorForm)))
          },

          codelang => {
            db.run(Languages.update(language, codelang)).map(_ =>
              Redirect(org.ieee_passau.controllers.routes.LanguageController.edit(codelang.id))
                .flashing("success" -> rs.messages("codelang.update.message", codelang.id))
            )
          }
        )
      case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }}

  val languageNewForm = Form(
    mapping(
      "id" -> nonEmptyText(maxLength = 30).verifying("codelang.id.error.taken", id => Languages.idAvailable(id)),
      "name" -> text(maxLength = 196).verifying(pattern(""".*, .*""".r, error = "codelang.name_and_version.error.pattern")),
      "highlightClass" -> text(maxLength = 30),
      "extension" -> nonEmptyText(maxLength = 10),
      "cpuFactor" -> of[Float],
      "memFactor" -> of[Float],
      "comment" -> text,
      "active"-> boolean
    )((id, name, highlightClass, extension, cpuFactor, memFactor, comment, active) => Language(id, name, highlightClass, extension, cpuFactor, memFactor, comment, active)
    )((l: Language) => Some((l.id, l.name, l.highlightClass, l.extension, l.cpuFactor, l.memFactor, l.comment, l.active)))
  )

  val languageUpdateForm = Form(
    mapping(
      "id" -> nonEmptyText(maxLength = 30),
      "name" -> nonEmptyText(maxLength = 196).verifying(pattern(""".*, .*""".r, error = "codelang.name_and_version.error.pattern")),
      "highlightClass" -> text(maxLength = 30),
      "extension" -> nonEmptyText(maxLength = 10),
      "cpuFactor" -> of[Float],
      "memFactor" -> of[Float],
      "comment" -> text,
      "active" -> boolean
    )((id, name, highlightClass, extension, cpuFactor, memFactor, comment, active) => Language(id, name, highlightClass, extension, cpuFactor, memFactor, comment, active))
    ((l: Language) => Some((l.id, l.name, l.highlightClass, l.extension, l.cpuFactor, l.memFactor, l.comment, l.active)))
  )
}
