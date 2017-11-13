package org.ieee_passau.controllers

import org.ieee_passau.forms.MaintenanceForms
import org.ieee_passau.models._
import org.ieee_passau.utils.PermissionCheck
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
import play.api.mvc._

object LanguageController extends Controller with PermissionCheck {

  def index: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.language.index(Languages.sortBy(_.id.asc).list))
  }}

  def edit(language: String): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Languages.byLang(language).map { lng =>
      Ok(org.ieee_passau.views.html.language.edit(lng.id, MaintenanceForms.languageUpdateForm.fill(lng)))
    }.getOrElse(NotFound(org.ieee_passau.views.html.errors.e404()))
  }}

  def insert: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    Ok(org.ieee_passau.views.html.language.insert(MaintenanceForms.languageUpdateForm))
  }}

  def save: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    MaintenanceForms.languageUpdateForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.language.insert(errorForm))
      },

      newTestcase => {
        Languages += newTestcase
        Redirect(org.ieee_passau.controllers.routes.LanguageController.edit(newTestcase.id))
          .flashing("success" -> Messages("codelang.create.message", newTestcase.id))
      }
    )
  }}

  def update(language: String): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
      MaintenanceForms.languageUpdateForm.bindFromRequest.fold(
      errorForm => {
        Languages.byLang(language).fold(Redirect(org.ieee_passau.controllers.routes.LanguageController.index()))(
          lng => BadRequest(org.ieee_passau.views.html.language.edit(lng.id, errorForm)))
      },

      codelang => {
        Languages.byLang(language).fold(Redirect(org.ieee_passau.controllers.routes.LanguageController.index()))(
          lng => {
            Languages.update(lng.copy(name = codelang.name, cpuFactor = codelang.cpuFactor, memFactor = codelang.cpuFactor))
            Redirect(org.ieee_passau.controllers.routes.LanguageController.edit(language))
              .flashing("success" -> Messages("codelang.update.message", codelang.id))
          }
        )
      }
    )
  }}
}
