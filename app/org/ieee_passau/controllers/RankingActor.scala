package org.ieee_passau.controllers

import java.util.Date

import akka.actor.Actor
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.controllers.MainController.{Highlight, HighlightSpecial, NoHighlight}
import org.ieee_passau.models._
import org.ieee_passau.utils.MathHelper
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._
import play.api.libs.concurrent.Akka

import scala.collection.SeqView
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class RankingActor extends Actor {

  val STARTUP_DELAY: FiniteDuration = 500 millis
  val TICK_INTERVAL: FiniteDuration = 1 minute

  // always calculate this first, so we can use it for the ranking
  private var problemsAll: List[((Int, Date, Date), Int, String, Double, EvalMode, Int, Int, Int, List[Int])] =
    calcProblemList(showHiddenUsers = true)
  private var problemsNormal: List[((Int, Date, Date), Int, String, Double, EvalMode, Int, Int, Int, List[Int])] =
    calcProblemList(showHiddenUsers = false)
  private var rankingAll: List[(Int, User, Double, Int)] = calcRanking(showAll = true)
  private var rankingNormal: List[(Int, User, Double, Int)] = calcRanking(showAll = false)

  private val tickSchedule = Akka.system.scheduler.schedule(STARTUP_DELAY, TICK_INTERVAL, self, UpdateRankingM)

  private def calcUserPoints(userProblemSolutions: Iterable[Map[Int, Seq[UserSolution]]], all:  Map[Int, SeqView[UserSolution, Seq[_]]]): Double = {
    userProblemSolutions.map { problem: Map[Int, Seq[UserSolution]] =>
      val head = problem.values.head.head
      // for each problem get best solution for each user based on sum of points for solved testcases and sum them up
      val sf = MathHelper.max(problem.map(solution => solution._2.map(test => test.points).sum).toList)
      head.evalMode match {
        case Static => sf

        case Dynamic =>
          val problemList = if (head.user.hidden) { problemsAll } else { problemsNormal }
          val problem = problemList.find(_._1._1 == head.problem).get
          (sf * calcChallengeFactor(problem._8, problem._7)) / 100

        case Best => 0 //TODO

        case NoEval => 0
      }
    }.sum
  }

  private def calcRanking(showAll: Boolean): List[(Int, User, Double, Int)] = { DB.withSession { implicit session =>
    val solutions = for {
      r <- Testruns       if r.result === (Passed: Result)
      c <- r.testcase     if c.visibility =!= (Hidden: Visibility)
      s <- r.solution
      u <- s.user         if u.hidden === false || (showAll: Boolean)
      p <- s.problem      if p.evalMode =!= (NoEval: EvalMode) || (showAll: Boolean)
    } yield (u, c.id, c.points, r.id, p.id, s.id, p.evalMode, r.score.?)

    val numTest: Map[Int, List[Testcase]] = Testcases.filter(t => t.visibility =!= (Hidden: Visibility)).list.groupBy(_.problemId)

    val users = solutions.list.view.map(x => UserSolution.tupled(x)).groupBy(_.user.id.get)
    users.map {
      case (_, values) =>
        val us = values.head
        val map = values.view.groupBy(_.problem).map(x => x._2.groupBy(_.solution))
        (
          us.user, calcUserPoints(map, users),
          // for each problem look if a solution exists for witch # of passed testcases equals # of testcases for this problem and count those problems
          map.filter(problem => problem.head._2.head.evalMode != NoEval).count(problem => problem.exists(solution => solution._2.size == numTest(solution._2.head.problem).size))
        )
    }.toList.view.sortBy(-_._2 /*points*/).zipWithIndex.groupBy(_._1._2 /*points*/).toList.flatMap {
      case (_, rankingPos) => for {
        ((user, points, solved), index) <- rankingPos
      } yield (rankingPos.head._2 + 1, user, points, solved)
    }.sortBy(_._4).sortBy(_._1)
  }}

  /**
    * We suppose a problem has exactly 100 points, users get between 100 and 50 points for a correct solution.
    * (The actual score is calculated from the success rate and the correctness of the solution, but that's irrelevant here)
    * see also https://www.hackerrank.com/scoring#Algorithmic%20Challenges
    * @param correct number of correct solutions
    * @param total number of total submissions
    * @return the challenge factor
    */
  private def calcChallengeFactor(correct: Int, total: Int): Double = {
    val sr = Try(correct.toDouble / total.toDouble).getOrElse(0D)
    val cf = 50 + (100 - 50) * (if (correct == 1 && total == 1) 1 else 1 - sr)
    cf
  }

  private def calcProblemPoints(mode: EvalMode, problemSolutions: SeqView[ProblemSolution, List[ProblemSolution]], total: Int, correct: Int): Double = {
    mode match {
      case Static => // sum up points over all testcases
        problemSolutions.groupBy(_.testcase).map(_._2.head.points).sum

      case Dynamic => calcChallengeFactor(correct, total)

      case Best => 0 //TODO

      case NoEval => 0
    }
  }

  private def calcProblemList(showHiddenUsers: Boolean) = { DB.withSession { implicit session =>
    val solutions = for {
      r <- Testruns
      c <- r.testcase     if c.visibility =!= (Hidden: Visibility)
      s <- r.solution
      u <- s.user         if u.hidden === false || (showHiddenUsers: Boolean)
      p <- s.problem
    } yield (p.door, p.title, c.points, c.id, s.id, s.userId, (p.id, p.readableStart, p.readableStop), r.result, p.evalMode)

    val testcases = for {
      (p, c) <- Problems leftJoin Testcases.filter(_.visibility =!= (Hidden: Visibility)) on (_.id === _.problemId)
    } yield (p.door, p.title, c.points.?, c.id.?, (p.id, p.readableStart, p.readableStop), p.evalMode)

    val solved = solutions.list.map(x => ProblemSolution.tupled(x)).groupBy(_.door)

    testcases.list.view.groupBy(_._1 /*door*/).map {
      case (problem, pInfo) =>
        if (solved.contains(problem)) {
          val problemSolutions = solved(problem).view
          val ps = problemSolutions.head
          // count number of submitted solutions
          val total = problemSolutions.groupBy(_.solution).values.size
          // count users with solutions
          val distinct = problemSolutions.groupBy(_.user).values.size
          // count users which have solutions that passed all testcases
          val correct = problemSolutions.groupBy(_.user).map(x => x._2.groupBy(_.solution))
            .count(user => user.exists(solution => solution._2.forall(_.result == Passed)))
          (
            ps.problem, ps.door, ps.title,
            calcProblemPoints(ps.evalMode, problemSolutions, distinct, correct),
            ps.evalMode, total, distinct, correct,
            // list all used which have passing solutions
            problemSolutions.groupBy(_.solution).filter(solution => solution._2.forall(_.result == Passed))
              .values.toList.groupBy(_.head.user).keySet.toList
          )
        } else {
          // problem does not yet have any submissions so we must insert an empty entry
          val (door, title, _, _, problem, mode) = pInfo.head
          (
            problem, door, title,
            // sum all possible points up
            pInfo.groupBy(_._4.getOrElse(0) /*testcase*/).map(_._2.head._3.getOrElse(0) /*points*/).sum.toDouble,
            mode, 0, 0, 0, List()
          )
        }
    }.toList
  }}

  override def postStop(): Unit = {
    super.postStop()
    this.tickSchedule.cancel()
  }

  override def receive: Receive = {
    case UpdateRankingM =>
      problemsAll = calcProblemList(showHiddenUsers = true)
      problemsNormal = calcProblemList(showHiddenUsers = false)
      rankingAll = calcRanking(showAll = true)
      rankingNormal = calcRanking(showAll = false)

    case ProblemsQ(uid, displayAll) =>
      val now = new Date()
      val list = if (displayAll) problemsAll else problemsNormal
      val problemList = list.filter(p => p._1._2.before(now) && p._1._3.after(now)).map {
        case (problem, door, title, points, mode, tries, distinctTries, correctCount, correctList) =>
          ProblemInfo(problem._1, door, title, points.floor.toInt, mode, tries, correctCount, correctList.contains(uid))
      }.sortBy(_.door)
      sender ! problemList

    case RankingQ(uid, displayAll) =>
      DB.withSession { implicit session =>
        val sessionUser = Users.byId(uid).firstOption
        val list =  if (displayAll) rankingAll else rankingNormal
        val ranking: List[(Int, String, Boolean, Int, Int, Int)] = list.map {
          case (index, user: User, points, solved) =>
            (index, user.username, user.hidden, points.floor.toInt, solved,
              if (sessionUser.isEmpty)
                NoHighlight
              else if (user.id.get == uid && sessionUser.get.hidden)
                HighlightSpecial
              else if (user.id.get == uid)
                Highlight
              else
                NoHighlight
            )
        }
        sender ! ranking
      }
  }
}
