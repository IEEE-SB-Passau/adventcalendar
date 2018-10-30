package org.ieee_passau.controllers

import java.nio.charset.MalformedInputException
import java.util.Date

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import com.google.inject.Inject
import com.google.inject.name.Named
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.models.DateSupport.dateMapper
import org.ieee_passau.models._
import org.ieee_passau.utils.FutureHelper.akkaTimeout
import org.ieee_passau.utils.{AkkaHelper, FormHelper, ListHelper, PermissionCheck}
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.{Lang, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.{Result, _}
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.io.File

class MainController @Inject()(val messagesApi: MessagesApi,
                               dbConfigProvider: DatabaseConfigProvider,
                               system: ActorSystem,
                               @Named(AkkaHelper.monitoringActor) monitoringActor: ActorRef,
                               @Named(AkkaHelper.rankingActor) rankingActor: ActorRef
                              ) extends Controller with PermissionCheck {
  private implicit val db: Database = dbConfigProvider.get[JdbcProfile].db
  private implicit val mApi: MessagesApi = messagesApi

  def problems: Action[AnyContent] = requirePermission(Everyone) { implicit user => Action.async { implicit rs =>
    val lang = request2lang
    val suid = if (user.isDefined) user.get.id.get else -1
    val unHide = user.isDefined && user.get.hidden
    val problems = (rankingActor ? ProblemsQ(suid, lang, unHide)).mapTo[List[ProblemInfo]]
    problems.flatMap { list =>
      (monitoringActor ? NotificationQ).mapTo[NotificationM].flatMap {
        case NotificationM(true) => Postings.byId(Page.notification.id, lang).map(_.content).map { notification =>
          Ok(org.ieee_passau.views.html.general.problemList(list, "notification" -> notification))
        }
        case _ => Future.successful(Ok(org.ieee_passau.views.html.general.problemList(list, "" -> "")))
      }
    }
  }}

  def ranking: Action[AnyContent] = requirePermission(Everyone) { implicit user => Action.async { implicit rs =>
    val suid = if (user.isDefined) user.get.id.get else -1
    val unHide = user.isDefined && user.get.hidden
    (rankingActor ? RankingQ(suid, displayHiddenUsers = unHide)).mapTo[List[(Int, String, Boolean, Int, Int, Int)]].flatMap { ranking =>
      (monitoringActor ? NotificationQ).mapTo[NotificationM].flatMap {
        case NotificationM(true) => Postings.byId(Page.notification.id, request2lang).map(_.content).map { notification =>
          Ok(org.ieee_passau.views.html.general.ranking(ranking, "notification" -> notification))
        }
        case _ => Future.successful(Ok(org.ieee_passau.views.html.general.ranking(ranking, "" -> "")))
      }
    }
  }}

  /**
    * Logic for handling submissions.
    *
    * @param door       the door of the problem
    * @param sourcecode the submitted sourcecode
    * @param filename   the file name of the source file, default: "", we will guess a proper name based on the language
    */
  def handleSubmission(door: Int, sourcecode: String, filename: String = "")(implicit rs: Request[MultipartFormData[TemporaryFile]], user: Option[User]): Future[Result] = {
    db.run(Problems.byDoor(door).result.headOption).flatMap {
      case Some(problem) if !problem.solvable => Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e403()))
      case Some(problem) =>
        val now = new Date()
        val lastSolutions: Future[Option[Solution]] = Solutions.getLatestSolutionByUser(user.get.id.get)
        val lastSolutionForProblem: Future[Option[Solution]] = Solutions.getLatestSolutionByUserAndProblem(user.get.id.get, problem.id.get)
        val solutionId: Future[Int] = lastSolutionForProblem.map {
          case Some(solution) => solution.id.get
          case None => -1
        }

        lastSolutions.flatMap {
          // only one submission every minute per task
          case Some(lastSolution) if lastSolution.created.after(new Date(now.getTime - 60000)) && !user.get.permission.includes(Moderator) =>
            Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door)).flashing("danger" ->
              (messagesApi("submit.ratelimit.message") + " " + messagesApi("submit.ratelimit.timer"))))
          case _ =>
            solutionId.flatMap { sid =>
              db.run((for {
                t <- Testruns if t.solutionId === sid && t.result === (Queued: org.ieee_passau.models.Result)
              } yield t.created).sortBy(_.desc).result.headOption)
            } flatMap {
              // if the last submission for this task is not evaluated yet, a new submission is only allowed every 15 minutes
              case Some(testCaseDate) if testCaseDate.after(new Date(now.getTime - 900000)) && !user.get.permission.includes(Moderator) =>
                Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door)).flashing("danger" ->
                  (messagesApi("submit.ratelimit.message") + " " + messagesApi("submit.ratelimit.queue"))))
              case _ =>
                Languages.byLang(rs.body.dataParts("lang").headOption.getOrElse("")).flatMap {
                  case Some(codelang) =>
                    // When using 2 proxies, the maximal possible remote-ip length with separators is 49 chars -> rounding up to 50
                    val remoteAddress = Some(rs.remoteAddress.take(50))
                    val userAgent = rs.headers.get("User-Agent").fold(None: Option[String])(ua => Some(ua.take(150)))

                    val fixedFilename =
                      // if it's jvm then that matches at least every valid classname and therefore filename, and all others don't matter that much anyway
                      if (!filename.isEmpty) filename.replaceAll("[^\\p{javaJavaIdentifierPart}.-]", "_")
                      // when using the editor, use "Solution.<ext>" as default, fixes jvm and every one else dosen't care
                      else "Solution." + codelang.extension

                    val pid = problem.id.get

                    try {
                      val solution: Future[Int] = db.run((Solutions returning Solutions.map(_.id)) +=
                        Solution(None, user.get.id.get, pid, codelang.id, sourcecode, fixedFilename, remoteAddress, userAgent, None, now))
                      solution.flatMap { solutionId =>
                        db.run(Testcases.filter(_.problemId === pid).map(_.id).result).map { testcases =>
                          testcases.foreach(t =>
                            db.run(Testruns += Testrun(None, solutionId, t, None, None, None, None, None, None, None, None, None, None, Queued, None, now, Some(0), None, None, now))
                          )
                        }
                      }
                    } catch {
                      case _ /*pokemon*/ : Throwable => // ignore
                        Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
                          .flashing("danger" -> (messagesApi("submit.error.message") + " " + messagesApi("submit.error.fileformat"))))
                    }

                    Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door).url + "#latest")
                      .flashing("success" -> messagesApi("submit.success.message")))

                  case _ => Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door)).flashing("danger" ->
                    (messagesApi("submit.error.message") + " " + messagesApi("submit.error.invalidlang"))))
                }
            }
        }
      case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }

  /**
    * Submits the provided solution and queues the corresponding test-runs.
    *
    * @param door the number of the calendar door this task is behind
    */
  def solveFile(door: Int): Action[MultipartFormData[TemporaryFile]] =
    requirePermission(Contestant, parse.multipartFormData) { implicit user => Action.async(parse.multipartFormData) { implicit rs =>
      rs.body.file("solution").map { submission =>
        val sourceFile = submission.ref.file
        if (sourceFile.length > 262144) {
          Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
            .flashing("danger" -> (messagesApi("submit.error.message") + " " + messagesApi("submit.error.filesize"))))
        } else {
          try {
            handleSubmission(door, File(sourceFile).slurp, submission.filename)
          } catch {
            case _: MalformedInputException =>
              Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
                .flashing("danger" -> (messagesApi("submit.error.message") + " " + messagesApi("submit.error.fileformat"))))
          }
        }
      } getOrElse {
        Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
          .flashing("danger" -> messagesApi("submit.error.message")))
      }
    }}

  /**
    * Submits the provided solution and queues the corresponding test-runs.
    *
    * @param door the number of the calendar door this task is behind
    */
  def solveString(door: Int): Action[MultipartFormData[TemporaryFile]] =
    requirePermission(Contestant, parse.multipartFormData) { implicit user => Action.async(parse.multipartFormData) { implicit rs =>
      rs.body.dataParts.find(_._1 == "submissiontext").map { submission =>
        if (submission._2.head.length > 262144) {
          Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
            .flashing("danger" -> (messagesApi("submit.error.message") + " " + messagesApi("submit.error.filesize"))))
        } else {
          handleSubmission(door, submission._2.head)
        }
      } getOrElse {
        Future.successful(Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
          .flashing("danger" -> messagesApi("submit.error.message")))
      }
    }}

  def codeEditor(door: Int, lang: String): Action[AnyContent] = Action.async { implicit rs =>
    Languages.byLang(lang).map {
      case Some(lng) => Ok(org.ieee_passau.views.html.solution.codeEditor(door, lng.extension))
      case _ => BadRequest("")
    }
  }

  /**
    * The "Fragen und Antworten" and "Loesung einreichen" sections of a problem.
    *
    * @param door the number of the calendar door this task is behind
    */
  def problemDetails(door: Int): Action[AnyContent] = requirePermission(Everyone) { implicit user => Action.async { implicit rs =>
    db.run(Problems.byDoor(door).result.headOption).flatMap {
      case None => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
      case Some(problem) =>
        if (!problem.readable) {
          Future.successful(Unauthorized(org.ieee_passau.views.html.errors.e404()))
        } else {
          val langs = db.run(Languages.sortBy(_.name).to[List].result)
          val uid = user.fold(-1)(u => u.id.get)
          val isMod = user.fold(false)(_.permission.includes(Moderator))

          // unanswered tickets
          val tickets = db.run((for {
            t: Tickets <- Tickets if t.problemId === problem.id && t.responseTo.?.isEmpty && (t.public === true || t.userId === uid || isMod)
            u: Users <- Users if u.id === t.userId
          } yield (t, u.username)).to[List].result)

          // answered tickets + answers
          val answers = db.run((for {
            pt: Tickets <- Tickets if pt.problemId === problem.id && (pt.public === true || pt.userId === uid || isMod)
            t: Tickets <- Tickets if t.responseTo === pt.id
            u: Users <- Users if u.id === t.userId
          } yield (t, u.username)).to[List].result)

          val allTickets = tickets.zip(answers).map(tuple => tuple._1 ++ tuple._2)

          val solutionsQuery = buildSolutionList(problem, uid)
          val lastAllSolutionQuery = db.run(Solutions.filter(_.userId === uid).sortBy(_.created.asc).result.headOption)
          // default language shown in the language selector
          val lastLang: Future[String] = solutionsQuery.zip(lastAllSolutionQuery).map { tuple =>
            val solutions = tuple._1
            val lastAllSolution = tuple._2
            if (solutions.nonEmpty) solutions.maxBy(_.solution.created).solution.language
            else if (user.nonEmpty && lastAllSolution.nonEmpty) lastAllSolution.get.language
            else "JAVA"
          }

          val displayLang: Lang = request2lang
          val transQuery: Future[Option[ProblemTranslation]] = db.run(ProblemTranslations.byProblemLang(problem.id.get, displayLang.code).result.headOption)
          val transProblem = transQuery.map(pt => pt.fold(problem)(trans => problem.copy(title = trans.title, description = trans.description)))
          val status = (monitoringActor ? StatusQ).mapTo[StatusM].flatMap {
            case StatusM(true) =>
              Postings.byId(Page.status.id, displayLang).map(_.content).map(status => "system" -> status)
            case _ => Future.successful("" -> "")
          }
          val notification = (monitoringActor ? NotificationQ).mapTo[NotificationM].flatMap {
            case NotificationM(true) =>
              Postings.byId(Page.notification.id, displayLang).map(_.content).map(notif => "notification" -> notif)
            case _ => Future.successful("" -> "")
          }
          val flash = status.zip(notification).map(tuple => Map(tuple._1, tuple._2))

          transProblem.flatMap(tp =>
            langs.flatMap(l =>
              lastLang.flatMap(ll =>
                solutionsQuery.flatMap(s =>
                  allTickets.flatMap(t =>
                    flash.map(f =>
                      Ok(org.ieee_passau.views.html.general.problemDetails(tp, l, ll, s, t, FormHelper.ticketForm, f))
                    )
                  )
                )
              )
            )
          )
        }
    }
  }}

  def getUserProblemSolutions(door: Int): Action[AnyContent] = requirePermission(Contestant) { implicit user => Action.async { implicit rs =>
    db.run(Problems.byDoor(door).result.headOption).flatMap {
      case Some(problem) =>
        val langsQuery = db.run(Languages.result)
        val solutionListQuery = buildSolutionList(problem, user.get.id.get)
        langsQuery.zip(solutionListQuery).map { tuple =>
          val langs = tuple._1.toList
          val solutionList = tuple._2
          val responseList = solutionList.take(1)
            .map(e => (e.solution.id.get, e.state.name, org.ieee_passau.views.html.solution.solutionList(List(e), langs).toString())) ++ solutionList.drop(1)
            .map(e => (e.solution.id.get, e.state.name, org.ieee_passau.views.html.solution.solutionList(List(e), langs, first = false).toString()))
          val json = Json.toJson(responseList.map(e => SolutionJSON.tupled(e)))
          Ok(json)
        }
      case _ => Future.successful(NotFound(org.ieee_passau.views.html.errors.e404()))
    }
  }}

  private def buildSolutionList(problem: Problem, userId: Int): Future[List[SolutionListEntry]] = {
    // submitted solutions
    db.run((for {
      c <- Testcases if c.problemId === problem.id && c.visibility =!= (Hidden: Visibility)
      s <- Solutions if s.problemId === problem.id && s.userId === userId
      r <- Testruns if c.id === r.testcaseId && s.id === r.solutionId
    } yield (s, c, r)).result).map { solutions =>
      ListHelper.buildSolutionList(solutions.toList)
    }
  }

  private implicit val SolutionJSONWrites: Writes[SolutionJSON] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "result").write[String] and
      (JsPath \ "html").write[String]
    ) (unlift(SolutionJSON.unapply))
}
