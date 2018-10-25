package org.ieee_passau.controllers

import java.nio.charset.MalformedInputException
import java.util.Date

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.controllers.EvaluationController.requirePermission
import org.ieee_passau.forms.{ProblemForms, UserForms}
import org.ieee_passau.models.DateSupport._
import org.ieee_passau.models.{Postings, _}
import org.ieee_passau.utils.{ListHelper, PermissionCheck}
import play.api.Play.current
import play.api.Routes
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Messages
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.concurrent.Akka
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.{Result, _}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.io.File

object MainController extends Controller with PermissionCheck {

  val rankingActor: ActorRef = Akka.system.actorOf(Props[RankingActor], "RankingActor")
  implicit val timeout = Timeout(5000 milliseconds)

  val NoHighlight = 0
  val Highlight = 1
  val HighlightSpecial = 2

  def jsRoutes: Action[AnyContent] = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        org.ieee_passau.controllers.routes.javascript.MainController.codeEditor,
        org.ieee_passau.controllers.routes.javascript.MainController.calendar
      )
    ).as("text/javascript")
  }

  def problems: Action[AnyContent] = Action.async { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)
    val suid = if (sessionUser.isDefined) sessionUser.get.id.get else -1
    val unHide = sessionUser.isDefined && sessionUser.get.hidden
    val problems = (rankingActor ? ProblemsQ(suid, request2lang, unHide)).mapTo[List[ProblemInfo]]
    problems.map(list => Ok(org.ieee_passau.views.html.general.problemList(list)))
  }

  def ranking: Action[AnyContent] = Action.async { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)
    val suid = if (sessionUser.isDefined) sessionUser.get.id.get else -1
    val unHide = sessionUser.isDefined && sessionUser.get.hidden
    val ranking = (rankingActor ? RankingQ(suid, displayHiddenUsers = unHide)).mapTo[List[(Int, String, Boolean, Int, Int, Int)]]
    ranking.map(list => Ok(org.ieee_passau.views.html.general.ranking(list)))
  }

  def calendar = DBAction { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)
    val displayLang = request2lang

    val now = new Date()
    val problems = Problems.filter(_.readableStart <= now).filter(_.readableStop > now).sortBy(_.door.asc).list
    val posting = Postings.byId(Page.calendar.id, displayLang).head

    // show all problems for debugging:
    //val problems = Query(Problems).sortBy(_.door.asc).list;

    Ok(org.ieee_passau.views.html.general.calendar(posting, problems))
  }

  /**
    * Display the requested page from the cms
    *
    * @param page the page to display
    */
  def content(page: String) = DBAction { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)
    val displayLang = request2lang

    val pageId = Page.withName(page).id
    val posting = Postings.byId(pageId, displayLang).head

    Ok(org.ieee_passau.views.html.general.content(posting))
  }

  /**
    * Logic for handling submissions.
    *
    * @param door       the door of the problem
    * @param sourcecode the submitted sourcecode
    * @param filename   the file name of the source file, default: "", we will guess a proper name based on the language
    */
  def handleSubmission(door: Int, sourcecode: String, filename: String = "")(implicit rs: DBSessionRequest[MultipartFormData[Files.TemporaryFile]]): Result = {

    implicit val sessionUser = getUserFromSession(request2session)
    if (sessionUser.isEmpty) {
      return Unauthorized(org.ieee_passau.views.html.errors.e403())
    }

    val problem = Problems.byDoor(door).firstOption
    if (problem.isEmpty) {
      return NotFound(org.ieee_passau.views.html.errors.e404())
    }

    if (!problem.get.solvable) {
      return Unauthorized(org.ieee_passau.views.html.errors.e403())
    }

    val now = new Date()
    val lastSolutions = Solutions.filter(_.userId === sessionUser.get.id.get).sortBy(_.created.desc)
    val lastLocalSolution = lastSolutions.filter(_.problemId === problem.get.id).sortBy(_.created.desc).firstOption
    val sid: Int = lastLocalSolution.fold(-1)(s => s.id.get)

    if (lastSolutions.firstOption.nonEmpty && lastSolutions.first.created.after(new Date(now.getTime - 60000)) && !sessionUser.get.permission.includes(Moderator)) {
      return Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door)).flashing("danger" ->
        (Messages("submit.ratelimit.message") + " " + Messages("submit.ratelimit.timer")))
    }

    val trs = (for {
      t <- Testruns if t.solutionId === sid && t.result === (Queued: org.ieee_passau.models.Result)
    } yield t.created).sortBy(_.desc).list

    if (trs.nonEmpty && trs.head.after(new Date(now.getTime - 900000)) && !sessionUser.get.permission.includes(Moderator)) {
      return Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door)).flashing("danger" ->
        (Messages("submit.ratelimit.message") + " " + Messages("submit.ratelimit.queue")))
    }

    val maybeCodelang = Languages.byLang(rs.body.dataParts("lang").headOption.getOrElse(""))
    if (maybeCodelang.isEmpty) {
      return Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door)).flashing("danger" ->
        (Messages("submit.error.message") + " " + Messages("submit.error.invalidlang")))
    }

    // When using 2 proxies, the maximal possible remote-ip length with separators is 49 chars -> rounding up to 50
    val remoteAddress = Some(rs.remoteAddress.take(50))
    val userAgent = rs.headers.get("User-Agent").fold(None: Option[String])(ua => Some(ua.take(150)))

    val codelang = maybeCodelang.get

    val fixedFilename =
      // if it's jvm then that matches at least every valid classname and therefore filename, and all others don't matter that much anyway
      if (!filename.isEmpty) filename.replaceAll("[^\\p{javaJavaIdentifierPart}.-]", "_")
      // when using the editor, use "Solution.<ext>" as default, fixes jvm and every one else dosen't care
      else "Solution." + codelang.extension

    try {
      val pid = Problems.byDoor(door).first.id.get

      val solution = (Solutions returning Solutions.map(_.id)) +=
        Solution(None, sessionUser.get.id.get, pid, codelang.id, sourcecode, fixedFilename, remoteAddress, userAgent, None, now)

      (for {
        t <- Testcases if t.problemId === pid
      } yield t.id).foreach(t =>
        Testruns += Testrun(None, solution, t, None, None, None, None, None, None, None, None, None, None, Queued, None, now, Some(0), None, None, now)
      )

    } catch {
      case _ /*pokemon*/: Throwable => // ignore
      return Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
        .flashing("danger" -> (Messages("submit.error.message") + " " + Messages("submit.error.fileformat")))
    }

    Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door).url + "#latest")
      .flashing("success" -> Messages("submit.success.message"))
  }

  /**
    * Submits the provided solution and queues the corresponding test-runs.
    *
    * @param door the number of the calendar door this task is behind
    */
  def solveFile(door: Int): Action[MultipartFormData[TemporaryFile]] = DBAction(parse.multipartFormData) { implicit rs =>
    rs.body.file("solution").map { submission =>
      val sourceFile = submission.ref.file
      if (sourceFile.length > 262144) {
        Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
          .flashing("danger" -> (Messages("submit.error.message") + " " + Messages("submit.error.filesize")))
      } else {
        try {
          handleSubmission(door, File(sourceFile).slurp, submission.filename)
        } catch {
          case _: MalformedInputException =>
            Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
              .flashing("danger" -> (Messages("submit.error.message") + " " + Messages("submit.error.fileformat")))
        }
      }
    } getOrElse {
      Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
        .flashing("danger" -> Messages("submit.error.message"))
    }
  }

  /**
    * Submits the provided solution and queues the corresponding test-runs.
    *
    * @param door the number of the calendar door this task is behind
    */
  def solveString(door: Int): Action[MultipartFormData[TemporaryFile]] = DBAction(parse.multipartFormData) { implicit rs =>
    rs.body.dataParts.find(_._1 == "submissiontext").map { submission =>
      if (submission._2.head.length > 262144) {
        Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
          .flashing("danger" -> (Messages("submit.error.message") + " " + Messages("submit.error.filesize")))
      } else {
        handleSubmission(door, submission._2.head)
      }
    } getOrElse {
      Redirect(org.ieee_passau.controllers.routes.MainController.problemDetails(door))
        .flashing("danger" -> Messages("submit.error.message"))
    }
  }

  def codeEditor(door: Int, lang: String): Action[AnyContent] = Action { implicit rs =>
    Languages.byLang(lang).fold(BadRequest(""))(lng => {
      Ok(org.ieee_passau.views.html.solution.codeEditor(door, lng.extension))
    })
  }

  /**
    * The "Fragen und Antworten" and "Loesung einreichen" sections of a problem.
    *
    * @param door the number of the calendar door this task is behind
    */
  def problemDetails(door: Int) = DBAction { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)

    Problems.byDoor(door).firstOption match {
      case None => NotFound(org.ieee_passau.views.html.errors.e404())
      case Some(problem) =>
        if (!problem.readable) {
          Unauthorized(org.ieee_passau.views.html.errors.e404())
        } else {
          val userId = if (sessionUser.isDefined) sessionUser.get.id.get else -1
          val langs = Languages.sortBy(_.name).list
          val uid = sessionUser match {
            case None => -1
            case Some(u) => u.id.get
          }
          val isMod = sessionUser.get.permission.includes(Moderator)

          // unanswered tickets
          var tickets = (for {
            t <- Tickets if t.problemId === problem.id && t.responseTo.?.isEmpty && (t.public === true || t.userId === uid || isMod)
            u <- Users if u.id === t.userId
          } yield (t, u.username)).list

          // answered tickets + answers
          tickets = tickets ++ (for {
            pt <- Tickets if pt.problemId === problem.id && (pt.public === true || pt.userId === uid || isMod)
            t <- Tickets if t.responseTo === pt.id
            u <- Users if u.id === t.userId
          } yield (t, u.username)).list

          val solutions = buildSolutionList(problem, userId)

          val lastAllSolution = Solutions.filter(_.userId === userId).sortBy(_.created.asc).firstOption
          // default language shown in the language selector
          val lastLang =
            if (solutions.nonEmpty) solutions.maxBy(_.solution.created).solution.language
            else if (sessionUser.nonEmpty && lastAllSolution.nonEmpty) lastAllSolution.get.language
            else "JAVA"

          val running = Await.result((EvaluationController.monitoringActor ? StatusQ).mapTo[StatusM], 100 millis)
          val displayLang = request2lang
          val trans = ProblemTranslations.byProblemLang(problem.id.get, displayLang.code).firstOption
          val transProblem = if (trans.nonEmpty) problem.copy(title=trans.get.title, description=trans.get.description) else problem
          val posting = Postings.byIdLang(Page.status.id, displayLang.code).firstOption
          val flash = if (!running.run) "system" -> (if (posting.nonEmpty) posting.get.content else Messages("status.messages.message")) else "" -> ""
          Ok(org.ieee_passau.views.html.general.problemDetails(transProblem, langs, lastLang, solutions, tickets, ProblemForms.ticketForm, flash))
        }
    }
  }

  def getUserProblemSolutions(door: Int): Action[AnyContent] = requirePermission(Contestant) { user => DBAction { implicit rs =>
    implicit val sessionUser = Some(user)
    Problems.byDoor(door).firstOption match {
      case None => NotFound(org.ieee_passau.views.html.errors.e404())
      case Some(problem) =>
        val langs = Languages.list

        val solutionList= buildSolutionList(problem, user.id.get)
        val responseList = solutionList.take(1)
            .map(e => (e.solution.id.get, e.state.name, org.ieee_passau.views.html.solution.solutionList(List(e), langs).toString())) ++
          solutionList.drop(1)
            .map(e => (e.solution.id.get, e.state.name, org.ieee_passau.views.html.solution.solutionList(List(e), langs, first = false).toString()))

        val json = Json.toJson(responseList.map(e => SolutionJSON.tupled(e)))
        Ok(json)
    }
  }}

  def feedback: Action[AnyContent] = requirePermission(Contestant) { user => Action { implicit rs =>
    implicit val sessionUser = Some(user)
    Ok(org.ieee_passau.views.html.general.feedback(UserForms.feedbackForm))
  }}

  def submitFeedback: Action[AnyContent] = requirePermission(Contestant) { user => DBAction { implicit rs =>
    implicit val sessionUser = Some(user)
    UserForms.feedbackForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.general.feedback(errorForm))
      },
      fb => {
        Feedbacks += Feedback(None, sessionUser.get.id.get, fb.rating, fb.pro, fb.con, fb.freetext)
        Redirect(org.ieee_passau.controllers.routes.MainController.calendar())
          .flashing("success" -> Messages("feedback.submit.message"))
      }
    )
  }}

  def buildSolutionList(problem: Problem, userId: Int)(implicit session: scala.slick.jdbc.JdbcBackend#SessionDef): List[SolutionListEntry]= {
    // submitted solutions
    val solutionsQuery = for {
      c <- Testcases if c.problemId === problem.id                          if c.visibility =!= (Hidden: Visibility)
      s <- Solutions if s.problemId === problem.id && s.userId === userId
      r <- Testruns if c.id === r.testcaseId && s.id === r.solutionId
    } yield (s, c, r)

    ListHelper.buildSolutionList(solutionsQuery.list)
  }

  implicit val SolutionJSONWrites: Writes[SolutionJSON] = (
      (JsPath \ "id").write[Int] and
      (JsPath \ "result").write[String] and
      (JsPath \ "html").write[String]
    ) (unlift(SolutionJSON.unapply))
}
