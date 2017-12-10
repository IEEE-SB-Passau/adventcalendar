package org.ieee_passau.controllers

import java.util.Date

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.ieee_passau
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.evaluation.Messages
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.forms.MaintenanceForms
import org.ieee_passau.models._
import org.ieee_passau.utils.ListHelper._
import org.ieee_passau.utils.{LanguageHelper, PermissionCheck}
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.i18n.Lang
import play.api.libs.concurrent.Akka
import play.api.mvc._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.xml.NodeSeq

object EvaluationController extends Controller with PermissionCheck {

  val monitoringActor: ActorRef = Akka.system.actorOf(Props[MonitoringActor], "MonitoringActor")
  implicit val timeout = Timeout(5000 milliseconds)

  private def sort(key: String, list: List[SubmissionListEntry]) = {
    key match {
      case "date" => list.sortBy(_.date)(Ordering[Date].reverse)
      case "problem" => list.sortBy(e => (e.door, e.user.toLowerCase(), e.lang.toLowerCase()))
      case "user" => list.sortBy(e => (e.user.toLowerCase(), e.door, e.lang.toLowerCase()))
      case "lang" => list.sortBy(e => (e.lang.toLowerCase(), e.user.toLowerCase(), e.door))
    }
  }

  def index(page: Int, ordering: String): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    val lng = request2lang

    val subsPerPage = 50

    val solutionsQuery = for {
      s <- Solutions
      tr <- Testruns if tr.solutionId === s.id
      p <- Problems if p.id === s.problemId
      u <- Users if u.id === s.userId
    } yield (s.id, s.language, u.username, p.id, p.door, p.title, s.created, tr.result, tr.stage.?)

    val solutions = solutionsQuery.list.view.groupBy(_._1).map { case (sid, sols) =>
      val solvedTestcases = sols.count(_._8 == Passed)
      val allTestcases = sols.length

      val title = ProblemTranslations.byProblemLang(sols.head._4/*problem*/, lng).firstOption.fold(sols.head._6)(_.title)

      val solved = sols.forall { case (_, _, _, _, _, _, _, r, _) => r == Passed }
      val failed = sols.exists { case (_, _, _, _, _, _, _, r, _) => r != Passed && r != Queued }
      val canceled = sols.forall { case (_, _, _, _, _, _, _, r, _) => r == Canceled || r == Passed }
      val queued = sols.exists { case (_, _, _, _, _, _, _, _, s) => s.isDefined }
      val state = if (queued)
        Queued
      else if (canceled && !solved)
        Canceled
      else if (failed)
        WrongAnswer
      else
        Passed
      SubmissionListEntry(sid, sols.head._2, sols.head._3, sols.head._5, title, sols.head._7, solvedTestcases, allTestcases, state)
    }.toList

    val sorted = sort(ordering, solutions)

    Ok(org.ieee_passau.views.html.solution.index(sorted.slice((page - 1) * subsPerPage,
      (page - 1) * subsPerPage + subsPerPage), (sorted.length / subsPerPage) + 1, page, ordering))
  }}

  def indexQueued: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    val jobs = Await.result((monitoringActor ? RunningJobsQ).mapTo[List[(Job, Date)]], 10 seconds)

    val testruns = (for {
      r <- Testruns if r.result === (Queued: ieee_passau.models.Result) || r.stage.?.isDefined
      s <- r.solution
      c <- r.testcase
      p <- c.problem
      u <- s.user
    } yield (r, c, p, s, u)).list

    val runningList = for {
      j <- jobs
      r <- testruns if r._1 /*testrun*/ .id.get == j._1 /*job*/ .testrunId
    } yield (r._1.id.get, r._4.id.get, r._2.position, r._1.stage.get, r._4.language, r._5.username, r._3.door, r._3.title, r._4.created, Some(j._2))

    val running = runningList.map(t => t._1)
    val list = runningList ++ (for {
      r <- testruns if !running.contains(r._1.id.get)
    } yield (r._1.id.get, r._4.id.get, r._2.position, r._1.stage.get, r._4.language, r._5.username, r._3.door, r._3.title, r._4.created, None))

    Ok(org.ieee_passau.views.html.monitoring.indexQueued(list.sortBy(_._9)(Ordering[Date]).sortBy(_._10)(Ordering[Option[Date]].reverse)))
  }}

  def details(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    val solutionsQuery = for {
      s <- Solutions if s.id === id
      tr <- Testruns if tr.solutionId === s.id
      tc <- Testcases if tc.id === tr.testcaseId
    } yield (s, tc, tr)

    val sol = buildSolutionList(solutionsQuery.list).head

    val user = Users.filter(_.id === sol.solution.userId).first

    val problem = Problems.filter(_.id === sol.solution.problemId).first

    Ok(org.ieee_passau.views.html.solution.solutionDetail(sol, Languages.list, user, problem))
  }}

  def vms: Action[AnyContent] = requireAdmin { admin => Action.async { implicit rs =>
    implicit val sessionUser = Some(admin)
    val list = Await.result((monitoringActor ? RunningVMsQ).mapTo[List[(String, Int, VMStatus)]], 10 seconds)

    val state = (monitoringActor ? StatusQ).mapTo[StatusM]
    state.map(running => Ok(org.ieee_passau.views.html.monitoring.vms(running.run, list.sortBy(_._1))))
  }}

  def stats: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    val jobs = (for {
      r <- Testruns if r.result =!= (Queued: ieee_passau.models.Result)
      s <- r.solution
    } yield (r.vm.?, r.completed, s.language)).list.view.flatMap {
      case job if job._1.getOrElse("").contains(" ") =>
        job._1.get.split(" ").zipWithIndex.map {
          case (vmName, idx) =>
            if (idx == 0) (vmName, job._2, job._3)
            else          (vmName, job._2, "BINARY")
        }.toList
      case job => List((job._1.get, job._2, job._3))
    }

    val oneHAgo = System.currentTimeMillis() - 3600 * 1000

    val jobs1H = jobs.filter(_._2.getTime >= oneHAgo)
    val numJobs1H = jobs1H.length
    val vmRank1H = jobs1H.groupBy(_._1).map {
      case (k, l) => (k, l.length)
    }.toList.sortBy(-_._2)
    val langRank1H = jobs1H.groupBy(_._3).map {
      case (k, l) => (k, l.length)
    }.toList.sortBy(-_._2)

    val numJobsFull = jobs.length
    val vmRankFull = jobs.groupBy(_._1).map {
      case (k, l) => (k, l.length)
    }.toList.sortBy(-_._2)
    val langRankFull = jobs.groupBy(_._3).map {
      case (k, l) => (k, l.length)
    }.toList.sortBy(-_._2)

    Ok(org.ieee_passau.views.html.monitoring.statistics(numJobs1H, vmRank1H, langRank1H, numJobsFull, vmRankFull, langRankFull))
  }}

  def maintenance: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    val displayLang = request2lang
    val state = Await.result((monitoringActor ? StatusQ).mapTo[StatusM], 50 millis)
    Postings.byIdLang(Page.status.id, displayLang.code).firstOption.map { post =>
      Ok(org.ieee_passau.views.html.monitoring.maintenance(state.run, post.content, Postings.list(LanguageHelper.defaultLanguage)))
    } getOrElse {
      Redirect(org.ieee_passau.controllers.routes.EvaluationController.editPage(Page.status.id, displayLang.code))
        .flashing("waring" -> play.api.i18n.Messages("posting.post.missing"))
    }
  }}

  def playPause: Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    val state = Await.result((monitoringActor ? StatusQ).mapTo[StatusM], 50 millis)
    MaintenanceForms.statusForm.bindFromRequest.fold(
      errorForm => {
        Redirect(org.ieee_passau.controllers.routes.EvaluationController.maintenance())
          .flashing("warning" -> play.api.i18n.Messages("status.update.error"))
      },
      status => {
        monitoringActor ! StatusM(status)
        Redirect(org.ieee_passau.controllers.routes.EvaluationController.maintenance())
          .flashing("success" -> play.api.i18n.Messages("status.update.message"))
      }
    )
  }}

  def createPage(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    val title = Postings.byId(id, LanguageHelper.defaultLanguage).head.title
    Ok(org.ieee_passau.views.html.monitoring.pageEditor(id, "", MaintenanceForms.postingForm, title))
  }}

  def editPage(id: Int, lang: String): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    Postings.byIdLang(id, lang).firstOption.map { post =>
      Ok(org.ieee_passau.views.html.monitoring.pageEditor(id, lang, MaintenanceForms.postingForm.fill(post)))
    } getOrElse {
      val post = Posting(Some(id), Lang(lang), "", "", new Date)
      Postings += post
      Ok(org.ieee_passau.views.html.monitoring.pageEditor(id, lang, MaintenanceForms.postingForm.fill(post)))
    }
  }}

  def addPage(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    MaintenanceForms.postingForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(org.ieee_passau.views.html.monitoring.pageEditor(id, "", errorForm))
      },
      posting => {
        if (Postings.byIdLang(id, posting.lang.code).firstOption.isDefined) {
          BadRequest(org.ieee_passau.views.html.monitoring.pageEditor(id, "", MaintenanceForms.postingForm.fill(posting)))
        } else {
          Postings += posting
          Redirect(org.ieee_passau.controllers.routes.EvaluationController.maintenance())
            .flashing("success" -> play.api.i18n.Messages("posting.update.message"))
        }
      }
    )
  }}

  def changePage(id: Int, lang: String): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    MaintenanceForms.postingForm.bindFromRequest.fold(
      errorForm => {
          BadRequest(org.ieee_passau.views.html.monitoring.pageEditor(id, lang, errorForm))
      },
      posting => {
        Postings.update(id, lang, posting.copy(title=Page.byId(id).toString))
        Redirect(org.ieee_passau.controllers.routes.EvaluationController.editPage(id, lang))
          .flashing("success" ->  play.api.i18n.Messages("posting.update.message"))
      }
    )
  }}

  def cancel(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)

    val maybeJob = Testruns.byId(id).firstOption
    maybeJob match {
      case None =>
        Redirect(org.ieee_passau.controllers.routes.EvaluationController.indexQueued())
          .flashing("warning" -> play.api.i18n.Messages("jobs.error.invalidjob"))
      case Some(job) =>
        Testruns.update(id, job.copy(result = Canceled, vm = Some("_"), evalId = None, completed = new Date, stage = None))
        monitoringActor ! JobFinished(BaseJob(0, id, job.evalId.getOrElse(""), "", "", "", "", ""))
        Redirect(org.ieee_passau.controllers.routes.EvaluationController.indexQueued())
          .flashing("success" -> play.api.i18n.Messages("jobs.control.cancel.message"))
    }
  }}

  def registerVM: Action[NodeSeq] = Action(parse.xml) { implicit rs =>
    implicit val sessionUser = getUserFromSession(request2session)
    if (sessionUser.isEmpty || !sessionUser.get.system) {
      Unauthorized(org.ieee_passau.views.html.errors.e403())
    }

    /*
    <ieee-advent-calendar>
      <node>
        <identifier>pentagram-2</identifier>
        <host>localhost</host>
        <port>22</port>
        <utilization>
          <users>5</users>
          <cpu>6</cpu>
          <memory>
            <virtual>9.800000</virtual>
            <swap>0.000000</swap>
          </memory>
        </utilization>
      </node>
    </ieee-advent-calendar>
    */

    // Read configuration
    val m = rs.body \\ "node"
    val host = (m \ "host").text
    val port = (m \ "port").text.toInt
    val actorName = (m \ "identifier").text
    val uri =  host + ":" + port

    Akka.system.actorSelection("user/Evaluator/VMMaster") ? NewVM(Messages.Config(actorName, host, port))

    val numUser = (m \\ "users").text.toInt
    val load = (m \\ "cpu").text.toFloat
    val mem = (m \\ "virtual").text.toFloat
    val swap = (m \\ "swap").text.toFloat

    monitoringActor ! VMStatusM(VMStatus(actorName, uri, numUser, load, mem, swap, new Date()))

    Ok("")
  }

  def removeVM(): Action[AnyContent] = requireSystem { system => Action { implicit rs =>
    val name = rs.body.asFormUrlEncoded.get("name").head
    Akka.system.actorSelection("user/Evaluator/VMMaster") ! RemoveVM(name)
    Ok("")
  }}

  def reEval(id: Int): Action[AnyContent] = requireAdmin { admin => DBAction { implicit rs =>
    implicit val sessionUser = Some(admin)
    (for {
      r <- Testruns
      s <- r.solution if s.id === id
    } yield r).list.map { testrun =>
      Testruns.update(testrun.id.get, testrun.copy(result = Queued, stage = Some(0), vm = None,
        progRuntime = Some(0), progMemory = Some(0), compRuntime = Some(0), compMemory = Some(0)))
    }
    Redirect(org.ieee_passau.controllers.routes.EvaluationController.index())
      .flashing("success" -> play.api.i18n.Messages("jobs.control.revaluate.message"))
  }}
}
