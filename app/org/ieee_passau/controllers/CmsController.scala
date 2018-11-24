package org.ieee_passau.controllers

import java.io.{File, IOException}
import java.nio.file.Paths
import java.util.Date

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import com.google.inject.Inject
import com.google.inject.name.Named
import org.apache.commons.io.FileUtils
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.models.DateSupport.dateMapper
import org.ieee_passau.models._
import org.ieee_passau.utils.FutureHelper.akkaTimeout
import org.ieee_passau.utils.LanguageHelper.LangTypeMapper
import org.ieee_passau.utils.{AkkaHelper, LanguageHelper}
import play.api.{Configuration, Environment}
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.Lang
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{Action, _}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class CmsController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                              val components: MessagesControllerComponents,
                              implicit val ec: ExecutionContext,
                              val config: Configuration,
                              val env: Environment,
                              val system: ActorSystem,
                              @Named(AkkaHelper.monitoringActor) val monitoringActor: ActorRef
                             ) extends MasterController(dbConfigProvider, components, ec, config, env) {

  def maintenance: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    val displayLang: Lang = rs.lang
    db.run(Postings.map(p => (p.id.?, p.lang, p.title, p.content, p.date)).to[List].result).flatMap { list: List[(Option[Int], Lang, String, String, Date)] =>
      val posts = list.groupBy(_._1 /*id*/).map(l =>
        (l._1.get, l._2.sortBy(_._2 /*lang*/)(LanguageHelper.ordering(LanguageHelper.defaultLanguage)).map(p => Posting.tupled(p))))
      (monitoringActor ? NotificationQ).mapTo[NotificationM].flatMap { notificationState =>
        Postings.byId(Page.notification.id, displayLang).map(_.content).map { notification =>
          Ok(org.ieee_passau.views.html.monitoring.maintenance(notificationState.run, notification, posts))
        }
      }
    }
  }}

  def toggleNotification: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    (monitoringActor ? NotificationT).mapTo[NotificationM].map { state =>
      if(state.run) db.run((for {u <- Users} yield u.notificationDismissed).update(false))

      Redirect(org.ieee_passau.controllers.routes.CmsController.maintenance())
        .flashing("success" -> rs.messages("status.notification.state." + (if (state.run) "on" else "off")))
    }
  }}

  def createPage(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    Postings.byIdOption(id, LanguageHelper.defaultLanguage).map {
      case Some(post) => Ok(org.ieee_passau.views.html.monitoring.pageEditor(id, "", postingForm, post.title))
      // TODO
      case _ => Ok(org.ieee_passau.views.html.monitoring.pageEditor(id, "", postingForm))
    }
  }}

  def editPage(id: Int, lang: String): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    Postings.byIdOption(id, Lang(lang)).flatMap {
      case Some(post) =>
        Future.successful(Ok(org.ieee_passau.views.html.monitoring.pageEditor(id, lang, postingForm.fill(post))))
      case _ =>
        val post = Posting(Some(id), Lang(lang), "", "", new Date)
        db.run(Postings += post).map(_ =>
          Ok(org.ieee_passau.views.html.monitoring.pageEditor(id, lang, postingForm.fill(post)))
        )
    }
  }}

  def addPage(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    postingForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(BadRequest(org.ieee_passau.views.html.monitoring.pageEditor(id, "", errorForm)))
      },
      posting => {
        Postings.byIdOption(id, posting.lang).flatMap {
          case Some(_) => Future.successful(BadRequest(org.ieee_passau.views.html.monitoring.pageEditor(id, "", postingForm.fill(posting))))
          case _ =>
          db.run(Postings += posting).map(_ =>
            Redirect(org.ieee_passau.controllers.routes.CmsController.maintenance())
              .flashing("success" -> rs.messages("posting.update.message"))
          )
        }
      }
    )
  }}

  def changePage(id: Int, lang: String): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    postingForm.bindFromRequest.fold(
      errorForm => {
          Future.successful(BadRequest(org.ieee_passau.views.html.monitoring.pageEditor(id, lang, errorForm)))
      },
      posting => {
        Postings.update(id, lang, posting.copy(title=Page.byId(id).toString)).map(_ =>
        Redirect(org.ieee_passau.controllers.routes.CmsController.editPage(id, lang))
          .flashing("success" ->  rs.messages("posting.update.message"))
        )
      }
    )
  }}

  def addFile(): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action { implicit rs =>
    Ok(org.ieee_passau.views.html.monitoring.upload())
  }}

  def uploadFile(): Action[MultipartFormData[TemporaryFile]] = requirePermission(Admin, parse.multipartFormData) { implicit admin => Action(parse.multipartFormData) { implicit rs =>
    rs.body.file("file").map { file =>
      val filename = Paths.get(file.filename).getFileName.toString.replaceAll("[^0-9A-z.\\-]", "_")
      val target = new File(config.getOptional[String]("play.assets.staticPath").getOrElse("/tmp/"), filename)
      try {
        FileUtils.copyFile(file.ref, target)

        Redirect(org.ieee_passau.controllers.routes.CmsController.listFiles(filename))
          .flashing("success" ->  rs.messages("assets.add.success"))
      } catch {
        case _: IOException => Redirect(org.ieee_passau.controllers.routes.CmsController.listFiles())
          .flashing("error" ->  rs.messages("assets.add.error"))
      }
    }.getOrElse {
      Redirect(org.ieee_passau.controllers.routes.CmsController.listFiles())
        .flashing("error" ->  rs.messages("assets.add.error"))
    }
  }}

  def deleteFile(filename: String): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action { implicit rs =>
    config.getOptional[String]("play.assets.staticPath")
    .fold(NotFound(org.ieee_passau.views.html.errors.e404())) { dir =>
      try {
        FileUtils.forceDelete(new File(dir, filename))
      } catch {
        case _: IOException => Redirect(org.ieee_passau.controllers.routes.CmsController.listFiles())
          .flashing("error" -> rs.messages("assets.remove.error"))
      }
      Redirect(org.ieee_passau.controllers.routes.CmsController.listFiles())
        .flashing("success" -> rs.messages("assets.remove.success"))
    }
  }}

  def listFiles(highlight: String): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action { implicit rs =>
    def recursiveListFiles(f: File): Array[File] = {
      val these = f.listFiles
      if (these == null) return Array()
      these.filter(_.isFile) ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
    }

    config.getOptional[String]("play.assets.staticPath")
      .fold(NotFound(org.ieee_passau.views.html.errors.e404())) { dir =>
        val base = new File(dir)
        val files = recursiveListFiles(base).map(f => base.toURI.relativize(f.toURI).toString)
        Ok(org.ieee_passau.views.html.monitoring.assetList(files, highlight))
      }
  }}

  /**
    * Display the requested page from the cms
    *
    * @param page the page to display
    */
  def content(page: String): Action[AnyContent] = requirePermission(Everyone) { implicit user => Action.async { implicit rs =>
    val displayLang = rs.lang
    db.run(Page.withName(page).id.result).flatMap { pageId =>
      Postings.byIdOption(pageId, displayLang)
    } map {
      case Some(posting: Posting) => Ok(org.ieee_passau.views.html.general.content(posting))
      case _ => NotFound(org.ieee_passau.views.html.errors.e404())
    }
  }}

  def calendar: Action[AnyContent] = requirePermission(Everyone) { implicit user =>  Action.async { implicit rs =>
    val displayLang = rs.lang
    val now = new Date()
    val problemsQuery: Future[List[Problem]] = db.run(Problems.filter(_.readableStart <= now).filter(_.readableStop > now).sortBy(_.door.asc).to[List].result)
    val postingQuery: Future[Posting] = Postings.byId(Page.calendar.id, displayLang)
    problemsQuery.zip(postingQuery).flatMap {
      case (problems, posting) => (monitoringActor ? NotificationQ).mapTo[NotificationM].flatMap {
        case NotificationM(true) => Postings.byId(Page.notification.id, displayLang).map(_.content).map { notification =>
          Ok(org.ieee_passau.views.html.general.calendar(posting, problems, "notification" -> notification))
        }
        case _ =>  Future.successful(Ok(org.ieee_passau.views.html.general.calendar(posting, problems, "" -> "")))
      }
    }
  }}

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
