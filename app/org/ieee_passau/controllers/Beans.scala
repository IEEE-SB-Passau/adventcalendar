package org.ieee_passau.controllers

import java.util.Date

import org.ieee_passau.models._
import play.api.i18n.Lang

object Beans {

  // Beans
  case class ProblemSolution(door: Int, points: Int, testcase: Int, solution: Int, user: Int, problem: (Int, Date, Date), result: Result, evalMode: EvalMode)

  case class UserSolution(user: User, testcase: Int, points: Int, testrun: Int, problem: Int, solution: Int, evalMode: EvalMode, score: Option[Int])

  case class SubmissionListEntry(id: Int, lang: String, user: String, door: Int, title: String, date: Date, passedTC: Int, allTC: Int, result: Result)

  case class ProblemInfo(id: Int, door: Int, name: String, points: Int, ownPoints: Int, evalMode: EvalMode, tries: Int, correctSolutions: Int, solved: Boolean)

  case class TestrunListEntry(position: Int, testcase: Testcase, testrun: Testrun)

  case class SolutionListEntry(position: Int, solution: Solution, testcases: List[TestrunListEntry])

  case class TicketText(text: String, public: Boolean)

  case class VMStatus(actorName: String, uri: String, users: Int, load: Float, mem: Float, swap: Float, ts: Date)

  case class SolutionJSON(id: Int, status: String, html: String)

  // Messages
  case class RunningJobsQ()

  case class RunningVMsQ()

  case class StatusM(run: Boolean)

  case class StatusQ()

  case class NotificationM(run: Boolean)

  case class NotificationQ()

  case class NotificationT()

  case class RankingQ(userId: Int)

  case class ProblemsQ(userId: Int, lang: Option[Lang] = None)

  case class VMStatusM(vMStatus: VMStatus)

  case class UpdateRankingM()

}
