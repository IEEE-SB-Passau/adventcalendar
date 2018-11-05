package org.ieee_passau.controllers

import java.util.Date

import akka.actor.{Actor, ActorSystem}
import com.google.inject.Inject
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.models.DateSupport.dateMapper
import org.ieee_passau.models.EvalMode.evalModeTypeMapper
import org.ieee_passau.models.Result.resultTypeMapper
import org.ieee_passau.models.Visibility.visibilityTypeMapper
import org.ieee_passau.models._
import org.ieee_passau.utils.ViewHelper.{Highlight, HighlightSpecial, NoHighlight}
import org.ieee_passau.utils.{FutureHelper, MathHelper}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.collection.SeqView
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.Try

class RankingActor @Inject() (val dbConfigProvider: DatabaseConfigProvider, val system: ActorSystem) extends Actor {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  implicit private val db: Database = dbConfig.db
  implicit private val evalContext: ExecutionContext = system.dispatchers.lookup("evaluator.context")

  val STARTUP_DELAY: FiniteDuration = 500 millis
  val TICK_INTERVAL: FiniteDuration = 1 minute

  private var problemsAll: List[((Int, Date, Date), Int, Double, EvalMode, Int, Int, Int, List[Int])] = List()
  private var problemsNormal: List[((Int, Date, Date), Int, Double, EvalMode, Int, Int, Int, List[Int])] = List()
  private var rankingAll: List[(Int, User, Double, Int)] = List()
  private var rankingNormal: List[(Int, User, Double, Int)] = List()
  private var userProblemPointsAll: Map[User, (Map[Int, Double], Int)] = Map()
  private var userProblemPointsNormal: Map[User, (Map[Int, Double], Int)] = Map()

  private val tickSchedule = system.scheduler.schedule(STARTUP_DELAY, TICK_INTERVAL, self, UpdateRankingM)

  private def calcUserPoints(userProblemSolutions: Iterable[Map[Int, Seq[UserSolution]]],
                             problemUserBestSubmission: Map[Int, Map[User, (Int, Int)]],
                             problemRankings:  Map[Int, Map[Int, Double]]) = {

    userProblemSolutions.map { problem: Map[Int, Seq[UserSolution]] =>
      val head = problem.values.head.head
      val sf = problemUserBestSubmission(head.problem)(head.user)._2
      (head.problem, head.evalMode match {
        case Static => sf

        case Dynamic =>
          val problemList = if (head.user.hidden) { problemsAll } else { problemsNormal }
          val problem = problemList.find(_._1._1 == head.problem).get
          (sf * calcChallengeFactor(problem._7, problem._6)) / 100

        case Best => problemRankings(head.problem)(sf) * 100

        case NoEval => 0

        case _ => 0
      })
    } .toMap
  }

  private def calcRanking(showAll: Boolean): List[(Int, User, Double, Int)] = {
    var userProblemPoints = calcUserPointsMap(showAll)

    val uids = userProblemPoints.map(_._1.id.get)

    val noPassedTCActivesQuery = for {
      r <- Testruns       if r.result =!= (Passed: org.ieee_passau.models.Result)
      c <- r.testcase     if c.visibility =!= (Hidden: Visibility)
      s <- r.solution
      u <- s.user         if u.hidden === false || (showAll: Boolean)
      p <- s.problem
    } yield (u, p)

    Await.result(db.run(noPassedTCActivesQuery.filterNot(_._1.id inSet uids).result).map { res =>
      res.groupBy(_._1).map {
        case (u, l) => (u, (l.map { case (_, p) => (p.id.get, 0d) }.toMap, 0))
      }
    } map {noPassedTCActives =>
      userProblemPoints = userProblemPoints ++ noPassedTCActives

      if (showAll)
        userProblemPointsAll = userProblemPoints
      else
        userProblemPointsNormal = userProblemPoints

      userProblemPoints.map {
        case (user, (userPoints, solved)) => (user, userPoints.values.sum, solved)
      }.toList.view.sortBy(-_._2 /*points*/).zipWithIndex.groupBy(_._1._2 /*points*/).toList.flatMap {
        case (_, rankingPos) => for {
          ((user, points, solved), _) <- rankingPos
        } yield (rankingPos.head._2 + 1, user, points, solved)
      }.sortBy(_._4).sortBy(_._1)
    }, FutureHelper.dbTimeout)
  }

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

  /**
    * Gives each user a score based on the reached points form all users.
    * Similar to the normalized integral of the histogram of best submission points
    * @param submissions map of user/points tuple
    * @return a map of points to rating factor
    */
  private def calcChallengeRankFactor(submissions: List[(User, (Int, Int))]) = {
    val sorted = submissions.map(x => (x._1, x._2._2)).sortBy(_._2 /* points */).groupBy(_._2 /* points */)
    val count = submissions.size

    // sum up the count of users with submissions for each point bracket of their best submission and divide the sum by
    // the total count of users with submissions starting from the bottom
    sorted.scanLeft((0, 0d, 0))((last, next) => (next._1, (last._3 + next._2.size).toDouble/count, last._3 + next._2.size)).map(x =>(x._1, x._2)).toMap
  }

  private def bestProblemUserSolution(problems:  Map[Int, SeqView[Beans.UserSolution, Seq[_]]]): Map[Int, Map[User, (Int, Int)]] = {
    problems.map { case (problem, problemSubmissions) =>
      // for each problem and each user get best solution for the user based on sum of points for solved testcases
      (problem, problemSubmissions.view.groupBy(_.user).map(x => (x._1, MathHelper.max(x._2.groupBy(_.solution).map(y => (y._2.map(_.points).sum, y._1)).toList))).map { case (pid, (points, sid)) => (pid, (sid, points))})
    }
  }

  private def calcUserPointsMap(showAll: Boolean): Map[User, (Map[Int, Double], Int)] = {
    val solutionsQuery = for {
      r <- Testruns       if r.result === (Passed: org.ieee_passau.models.Result)
      c <- r.testcase     if c.visibility =!= (Hidden: Visibility)
      s <- r.solution
      u <- s.user         if u.hidden === false || (showAll: Boolean)
      p <- s.problem      if p.evalMode =!= (NoEval: EvalMode) || (showAll: Boolean)
    } yield (u, c.id, c.points, r.id, p.id, s.id, p.evalMode, r.score.?)

    Await.result(db.run(solutionsQuery.to[List].result).flatMap { solutions =>
      val userSolutions = solutions.view.map(x => UserSolution.tupled(x))
      val users = userSolutions.groupBy(_.user.id.get)
      val problemUserSolutions = bestProblemUserSolution(userSolutions.groupBy(_.problem))
      db.run(Testcases.filter(t => t.visibility =!= (Hidden: Visibility)).to[List].result).flatMap { numTestList =>
        val numTest = numTestList.groupBy(_.problemId)
        db.run(Problems.filter(_.evalMode === (Best: EvalMode)).map(_.id).to[List].result).map { problemRankingList =>
          val problemRanking = problemRankingList.map(p => (p, calcChallengeRankFactor(problemUserSolutions(p).toList))).toMap
          users.map {
            case (_, values) =>
              val user = values.head.user
              val map = values.view.groupBy(_.problem).map(x => x._2.groupBy(_.solution))
              (
                user, (calcUserPoints(map, problemUserSolutions, problemRanking),
                // for each problem look if a solution exists for witch # of passed testcases equals # of testcases for this problem and count those problems
                map.filter(problem => problem.head._2.head.evalMode != NoEval).count(problem => problem.exists(solution => solution._2.size == numTest(solution._2.head.problem).size))
              ))
          }
        }
      }
    }, FutureHelper.dbTimeout)
  }

  private def calcProblemPoints(mode: EvalMode, problemSolutions: SeqView[ProblemSolution, List[ProblemSolution]], total: Int, correct: Int): Double = {
    mode match {
      case Static | Best => // sum up points over all testcases
      problemSolutions.groupBy(_.testcase).map(_._2.head.points).sum

      case Dynamic => calcChallengeFactor(correct, total)

      case NoEval => 0

      case _ => 0
    }
  }

  private def calcProblemList(showHiddenUsers: Boolean): List[((Int, Date, Date), Int, Double, EvalMode, Int, Int, Int, List[Int])] = {
    val solutionsQuery = for {
      r <- Testruns
      c <- r.testcase     if c.visibility =!= (Hidden: Visibility)
      s <- r.solution
      u <- s.user         if u.hidden === false || (showHiddenUsers: Boolean)
      p <- s.problem
    } yield (p.door, c.points, c.id, s.id, s.userId, (p.id, p.readableStart, p.readableStop), r.result, p.evalMode)

    Await.result(db.run(solutionsQuery.to[List].result).flatMap { solutions =>
      val solved = solutions.map(x => ProblemSolution.tupled(x)).groupBy(_.door)

      val testcasesQuery = for {
        (p, c) <- Problems joinLeft Testcases.filter(_.visibility =!= (Hidden: Visibility)) on (_.id === _.problemId)
      } yield (p.door, c.map(_.points), c.map(_.id), (p.id, p.readableStart, p.readableStop), p.evalMode)

      db.run(testcasesQuery.to[List].result).map { testcases: List[(Int, Option[Int], Option[Int], (Int, Date, Date), EvalMode)] =>
        testcases.groupBy(_._1 /*door*/).map {
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
                ps.problem, ps.door,
                calcProblemPoints(ps.evalMode, problemSolutions, distinct, correct),
                ps.evalMode, total, distinct, correct,
                // list all used which have passing solutions
                problemSolutions.groupBy(_.solution).filter(solution => solution._2.forall(_.result == Passed))
                  .values.toList.groupBy(_.head.user).keySet.toList
              )
            } else {
              // problem does not yet have any submissions so we must insert an empty entry
              val (door, _, _, problem, mode) = pInfo.head
              (
                problem, door,
                // sum all possible points up
                pInfo.groupBy(_._3.getOrElse(0) /*testcase*/).map(_._2.head._2.getOrElse(0) /*points*/).sum.toDouble,
                mode, 0, 0, 0, List()
              )
            }
        }.toList
      }
    }, FutureHelper.dbTimeout)
  }

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

    case ProblemsQ(uid, lang, displayAll) =>
      val source = sender
      val now = new Date()
      val list = (if (displayAll) problemsAll else problemsNormal).filter(p => p._1._2.before(now) && p._1._3.after(now))
      val list2 = if (displayAll) userProblemPointsAll else userProblemPointsNormal
      ProblemTranslations.problemTitleListByLang(lang).flatMap { transList =>
        db.run(Users.byId(uid).result.headOption).map { sessionUser => list.map {
          case (problem, door, points, mode, tries, _, correctCount, correctList) =>
            val ownPoints = sessionUser.fold(0)(user => list2.get(user).fold(0)(_._1.get(problem._1).fold(0)(_.floor.toInt)))
            val problemTitle = transList.getOrElse(problem._1, "")
            ProblemInfo(problem._1, door, problemTitle, points.floor.toInt, ownPoints, mode, tries, correctCount, correctList.contains(uid))
        }}
      } map(problemList => source ! problemList.sortBy(_.door))

    case RankingQ(uid, displayAll) =>
      val list =  if (displayAll) rankingAll else rankingNormal
      val source = sender
      db.run(Users.byId(uid).result.headOption).map { sessionUser =>
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
        source ! ranking
      }
  }
}
