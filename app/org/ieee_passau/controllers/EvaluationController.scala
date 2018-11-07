package org.ieee_passau.controllers

import java.util.Date

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import com.google.inject.Inject
import com.google.inject.name.Named
import javax.inject.Singleton
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models.DateSupport.dateMapper
import org.ieee_passau.models.{Admin, _}
import org.ieee_passau.utils.AkkaHelper
import org.ieee_passau.utils.FutureHelper.akkaTimeout
import org.ieee_passau.utils.ListHelper._
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.xml.NodeSeq

@Singleton
class EvaluationController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                     val components: MessagesControllerComponents,
                                     implicit val ec: ExecutionContext,
                                     val system: ActorSystem,
                                     @Named(AkkaHelper.monitoringActor) val monitoringActor: ActorRef,
                                     @Named(AkkaHelper.rankingActor) val rankingActor: ActorRef
                                    ) extends MasterController(dbConfigProvider, components, ec) {

  private def sort(key: String, list: List[SubmissionListEntry]) = {
    key match {
      case "date" => list.sortBy(_.date)(Ordering[Date].reverse)
      case "problem" => list.sortBy(e => (e.door, e.user.toLowerCase(), e.lang.toLowerCase()))
      case "user" => list.sortBy(e => (e.user.toLowerCase(), e.door, e.lang.toLowerCase()))
      case "lang" => list.sortBy(e => (e.lang.toLowerCase(), e.user.toLowerCase(), e.door))
    }
  }

  def index(page: Int, ordering: String): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    val lang = rs.lang

    val subsPerPage = 50

    val solutionsQuery = for {
      s <- Solutions
      tr <- Testruns if tr.solutionId === s.id
      p <- Problems if p.id === s.problemId
      u <- Users if u.id === s.userId
    } yield (s.id, s.language, u.username, p.id, p.door, s.created, tr.result, tr.stage.?)
    ProblemTranslations.problemTitleListByLang(lang).flatMap { transList =>
      db.run(solutionsQuery.to[List].result).map { rawSolutions: List[(Int, String, String, Int, Int, Date, org.ieee_passau.models.Result, Option[Int])] =>
        val solutions = rawSolutions.groupBy(_._1).map { case (sid, sols) =>
          val solvedTestcases = sols.count(_._7 == Passed)
          val allTestcases = sols.length
          val title = transList.getOrElse(sols.head._4, "")
          val solved = sols.forall { case (_, _, _, _, _, _, r, _) => r == Passed }
          val failed = sols.exists { case (_, _, _, _, _, _, r, _) => r != Passed && r != Queued }
          val canceled = sols.forall { case (_, _, _, _, _, _, r, _) => r == Canceled || r == Passed }
          val queued = sols.exists { case (_, _, _, _, _, _, _, s) => s.isDefined }
          val state = if (queued)
            Queued
          else if (canceled && !solved)
            Canceled
          else if (failed)
            WrongAnswer
          else
            Passed
          SubmissionListEntry(sid, sols.head._2, sols.head._3, sols.head._5, title, sols.head._6, solvedTestcases, allTestcases, state)
        }.toList

        val sorted = sort(ordering, solutions)

        Ok(org.ieee_passau.views.html.solution.index(sorted.slice((page - 1) * subsPerPage,
          (page - 1) * subsPerPage + subsPerPage), (sorted.length / subsPerPage) + 1, page, ordering))
      }
    }
  }}

  def indexQueued: Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    val testrunsQuery = for {
      r <- Testruns if r.result === (Queued: org.ieee_passau.models.Result) || r.stage.?.isDefined
      s <- r.solution
      c <- r.testcase
      p <- c.problem
      u <- s.user
    } yield (r, c, p, s, u)

    (monitoringActor ? RunningJobsQ).mapTo[List[(Job, Date)]].flatMap { jobs =>
      db.run(testrunsQuery.to[List].result).map { testruns =>
        val runningList = for {
          j <- jobs
          r <- testruns if r._1 /*testrun*/ .id.get == j._1 /*job*/ .testrunId
        } yield (r._1.id.get, r._4.id.get, r._2.position, r._1.stage.get, r._4.language, r._5.username, r._3.door, r._3.title, r._4.created, Some(j._2))
        val running = runningList.map(t => t._1)
        val list = runningList ++ (for {
          r <- testruns if !running.contains(r._1.id.get)
        } yield (r._1.id.get, r._4.id.get, r._2.position, r._1.stage.get, r._4.language, r._5.username, r._3.door, r._3.title, r._4.created, None))
        Ok(org.ieee_passau.views.html.monitoring.indexQueued(list.sortBy(_._9)(Ordering[Date]).sortBy(_._10)(Ordering[Option[Date]].reverse)))
      }
    }
  }}

  def details(id: Int): Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    val solutionsQuery = for {
      s <- Solutions if s.id === id
      tr <- Testruns if tr.solutionId === s.id
      tc <- Testcases if tc.id === tr.testcaseId
    } yield (s, tc, tr)
    db.run(solutionsQuery.to[List].result).flatMap { solutionsList =>
      val sol = buildSolutionList(solutionsList).head
      val userQ = Users.filter(_.id === sol.solution.userId).result.head
      val problemQ = Problems.filter(_.id === sol.solution.problemId).result.head
      val languageQ = Languages.to[List].result
      db.run(userQ).flatMap { user =>
        db.run(problemQ).flatMap { problem =>
          db.run(languageQ).map { langs =>
            Ok(org.ieee_passau.views.html.solution.solutionDetail(sol, langs, user, problem))
          }
        }
      }
    }
  }}

  def vms: Action[AnyContent] = requirePermission(Admin){ implicit admin => Action.async { implicit rs =>
    (monitoringActor ? RunningVMsQ).mapTo[List[(String, Int, VMStatus)]] flatMap {list =>
      (monitoringActor ? StatusQ).mapTo[StatusM].map(running =>
        Ok(org.ieee_passau.views.html.monitoring.vms(running.run, list.sortBy(_._1))))
    }
  }}

  def stats: Action[AnyContent] = requirePermission(Moderator) { implicit admin => Action.async { implicit rs =>
    db.run((for {
      r <- Testruns if r.result =!= (Queued: org.ieee_passau.models.Result)
      s <- r.solution
    } yield (r.vm.?, r.completed, s.language)).to[List].result).map { rawJobs: List[(Option[String], Date, String)] =>
      val jobs = rawJobs.flatMap {
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
    }
  }}

  def cancel(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run(Testruns.byId(id).result.headOption) map {
      case Some(job) =>
        Testruns.update(id, job.copy(result = Canceled, vm = Some("_"), evalId = None, completed = new Date, stage = None))
        monitoringActor ! JobFinished(BaseJob(0, 0, "", id, job.evalId.getOrElse(""), "", "", "", ""))
        Redirect(org.ieee_passau.controllers.routes.EvaluationController.indexQueued())
          .flashing("success" -> rs.messages("jobs.control.cancel.message"))
      case _ =>
        Redirect(org.ieee_passau.controllers.routes.EvaluationController.indexQueued())
          .flashing("warning" -> rs.messages("jobs.error.invalidjob"))
    }
  }}

  def reEval(id: Int): Action[AnyContent] = requirePermission(Admin) { implicit admin => Action.async { implicit rs =>
    db.run((for {
      r <- Testruns
      s <- r.solution if s.id === id
    } yield (r, s)).result).map { testruns =>
      Future.reduceLeft(testruns.map { case (testrun, solution) =>
        Solutions.update(solution.id.get, solution.copy(score = 0))
        Testruns.update(testrun.id.get, testrun.copy(result = Queued, stage = Some(0), vm = None,
          progRuntime = Some(0), progMemory = Some(0), compRuntime = Some(0), compMemory = Some(0)))
      }.toList)(_)
    }.map(_ =>
      Redirect(org.ieee_passau.controllers.routes.EvaluationController.index())
        .flashing("success" -> rs.messages("jobs.control.revaluate.message"))
    )
  }}

  def registerVM: Action[NodeSeq] = requirePermission(Internal, parse.xml) { _ => Action[NodeSeq](parse.xml) { implicit rs =>

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

    monitoringActor ? NewVM(Config(actorName, host, port))

    val numUser = (m \\ "users").text.toInt
    val load = (m \\ "cpu").text.toFloat
    val mem = (m \\ "virtual").text.toFloat
    val swap = (m \\ "swap").text.toFloat

    monitoringActor ! VMStatusM(VMStatus(actorName, uri, numUser, load, mem, swap, new Date()))

    Ok("")
  }}

  def removeVM(): Action[AnyContent] = requirePermission(Internal) { _ => Action { implicit rs =>
    val name = rs.body.asFormUrlEncoded.get("name").head
    monitoringActor ! RemoveVM(name)
    Ok("")
  }}
}
