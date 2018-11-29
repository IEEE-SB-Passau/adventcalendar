package org.ieee_passau.controllers

import java.util.Date

import akka.actor.{Actor, ActorSystem}
import akka.pattern.pipe
import com.google.inject.Inject
import org.ieee_passau.controllers.Beans._
import org.ieee_passau.models.DateSupport.dateMapper
import org.ieee_passau.models.EvalMode.evalModeTypeMapper
import org.ieee_passau.models._
import org.ieee_passau.utils.LanguageHelper
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcProfile, PostgresProfile}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class RankingActor @Inject() (val dbConfigProvider: DatabaseConfigProvider, val system: ActorSystem) extends Actor {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  implicit private val db: Database = dbConfig.db
  implicit private val evalContext: ExecutionContext = system.dispatchers.lookup("evaluator.context")

  val STARTUP_DELAY: FiniteDuration = 500 millis
  val TICK_INTERVAL: FiniteDuration = 1 minute

  private val tickSchedule = system.scheduler.schedule(STARTUP_DELAY, TICK_INTERVAL, self, UpdateRankingM)

  /*
  * score     => sum of testcase points of that problem for the best solution of that user on that problem
  * points    => final points in ranking for that user on that problem
  * isSolved  => best submissions for that user has all testcases for that problem correct
  * correct   => number of users with submissions that are correct for all testcases for that problem
  * userTries => number of users with at least one submission to that problem
  * solved    => number of solved problems of that user
  * total     => number of all tries of all users on that problem
  * totalUser => number of all tries of that user on that problem
  */
  private val ranking:       mutable.Map[Boolean /*isHidden*/, Map[Int /*userId*/, Map[Int /*problemId*/, (Int /*score*/, Double /*points*/, Boolean /*isSolved*/, EvalMode)]]] = mutable.Map()
  private val problemCounts: mutable.Map[Boolean /*isHidden*/, Map[Int /*problemId*/, (Int /*correct*/, Int /*userTries*/, Int /*total*/)]] = mutable.Map()
  private var userCorrectSolutions: Map[Int /*userId*/, Int /*solved*/] = Map()
  // Variant with problem list tries = user tries
  // private var userXsubmissions: Map[Int /*userId*/ , (Int /*solved*/ , Map[Int /*problemId*/ , Int /*totalUser*/ ])] = Map(

  private var stats: StatsM = StatsM(0, List(), List(), 0, List(), List(), (0, 0), List())

  /**
    * We suppose a problem has exactly `points` points, users get between `points` and `baseLine = 0.5 * points` points
    * for a correct solution.
    * (The actual score is later calculated from the success rate and the correctness of the solution, but that's
    * irrelevant here)
    * see also https://www.hackerrank.com/scoring#Algorithmic%20Challenges
    *
    * @param points  the max points of the problem
    * @param correct number of correct solutions
    * @param users   number of total submissions
    * @param total   ignored
    * @return the challenge factor
    */
  private def calculateChallengeFactor(points: Int)(correct: Int, users: Int, total: Int): Double = {
    val baseLine = 0.5
    val sr = Try(correct.toDouble / users.toDouble).getOrElse(0D)
    val cf = points * baseLine + (points - points * baseLine) * (if (correct == 1 && users == 1) 1 else 1 - sr)
    Try(cf / points).getOrElse(0D)
  }

  /**
    * Gives each user a score based on the reached points form all users.
    * Similar to the normalized integral of the histogram of best submission points
    *
    * @param submissions map of user/points tuple
    * @return a map of points to rating factor
    */
  private def calculateChallengeRank(submissions: List[Int]) = {
    val sorted = submissions.sorted.groupBy(x => x)
    val count = submissions.size

    // sum up the count of users with submissions for each point bracket of their best submission and divide the sum by
    // the total count of users with submissions starting from the bottom
    sorted.scanLeft((0, 0d, 0))((last, next) => (next._1, (last._3 + next._2.size).toDouble / count, last._3 + next._2.size)).map(x => (x._1, x._2)).toMap
  }

  /**
    * Calculates the maximal possible points for this problem at this time
    *
    * @param mode    the eval mode
    * @param points  the max points of the problem
    * @param correct the number of correct submissions for this problem
    * @param users   the number of total submissions for this problem
    * @param total   ignored
    * @return the points if this problem would be solved at this time
    */
  private def calculateMaxProblemPoints(mode: EvalMode, points: Int)(correct: Int, users: Int, total: Int): Double = {
    mode match {
      case Static | Best => points
      case Dynamic => calculateChallengeFactor(points)(correct, users, total) * points
      case NoEval | _ => 0
    }
  }

  private def buildCache(includeHidden: Boolean): Unit = {
    def simplify[A, B, C](query: Query[(Rep[A], Rep[B], Rep[C]), (A, B, C), scala.Seq]) = {
      db.run(query.result).map(l => l.groupBy(_._1).map { case (p, sl) => (p, (sl.head._2, sl.head._3)) })
    }

    def transpose[T](mapOfMaps: Map[Int, Map[Int, T]]) = {
      // TODO make more efficient
      mapOfMaps.view
        // deconstruct
        .flatMap { case (a, bs) => bs.map { case (b, c) => (b, a, c) } }
        // rebuild
        .groupBy(_._1).map { case (b, as) =>
        b -> as.groupBy(_._2).map { case (a, cs) =>
          a -> Some(cs.head._3)
        }.withDefaultValue(None)
      }.withDefaultValue(Map().withDefaultValue(None))
    }

    def bestUserProblemSubmission(includeHidden: Boolean) = {
      val data = for {
        s <- Solutions
        p <- s.problem
        u <- s.user if u.hidden === false || (includeHidden: Boolean)
      } yield (u.id, p.id, s.score, p.evalMode)

      db.run(data.result).map { list =>
        ranking(includeHidden) = list.groupBy(_._1).map { case (u, pl) =>
          u -> pl.groupBy(_._2).map { case (p, sl) =>
            p -> (sl.map(_._3).max, 0d, false, sl.head._4)
          }.withDefaultValue((0, 0d, false, NoEval))
        }.withDefaultValue(Map().withDefaultValue((0, 0d, false, NoEval)))
      }
    }

    val correctSubmissions = (for {
      s <- Solutions if s.result === (Passed: Result)
      p <- s.problem
      u <- s.user if u.hidden === false || (includeHidden: Boolean)
    } yield (p.id, s.id, u.id)).distinctOn(x => (x._1, x._3))

    val allSubmissions = for {
      s <- Solutions
      p <- s.problem
      u <- s.user if u.hidden === false || (includeHidden: Boolean)
    } yield (p.id, s.id, u.id)

    val problemList = for {p <- Problems} yield (p.id, p.evalMode, p.points)

    bestUserProblemSubmission(includeHidden).foreach { _ =>
      val problemXuserXbest = transpose(ranking(includeHidden))
      simplify(problemList).foreach { modeL =>
        db.run(correctSubmissions.result).foreach { correctC =>
          db.run(allSubmissions.result).foreach { allS =>
            val modeList = modeL.view
            val correctCount = correctC.view.groupBy(_._1).map { case (p, ul) => p -> ul.groupBy(_._3).map { case (u, l) => u -> l.length } }
            val userXproblemXsolved = transpose(correctCount)
            val submissionCounts = allS.view.groupBy(_._1 /*problem*/).map { case (p, l) =>
              p -> (correctCount.get(p).fold(0)(_.keySet.size), l.groupBy(_._3 /*user*/).keySet.size, l.length)
            }
            // This would be the number of tries for a problem for each user
            // val userXproblemXcount = allS.groupBy(_._3).map { case (u, pl) => u -> pl.groupBy(_._1).map { case (p, sl) => p -> sl.length } }
            val dynamicChallengeFactor = modeList.filter(_._2._1 == Dynamic).map { case (p, (_, points)) =>
              p -> (calculateChallengeFactor(points) _).tupled(submissionCounts(p))
            }.toMap
            val bestChallengeFactor = modeList.filter(_._2._1 == Best).map { case (p, _) =>
              p -> calculateChallengeRank(problemXuserXbest(p).values.map(_.fold(0)(_._1)).toList)
            }.toMap

            problemCounts(includeHidden) = submissionCounts.withDefaultValue((0,0,0))

            ranking(includeHidden) = ranking(includeHidden).map { case (u, pl) =>
              u -> pl.map { case (p, (score, _, _, mode)) =>
                p -> (score, mode match {
                  case Static => score
                  case Dynamic => dynamicChallengeFactor(p) * score
                  case Best => bestChallengeFactor(p)(score)
                  case NoEval | _ => 0
                }, userXproblemXsolved(u)(p).fold(false)(_ > 0), mode)
              }.withDefaultValue((0, 0d, false, NoEval))
            }.withDefaultValue(Map().withDefaultValue((0, 0d, false, NoEval)))

            // use userXproblemXcount here
            userCorrectSolutions = ranking(true).map { case (u, pl) =>
              u -> pl.count {
                case (_, (_, _, true, mode)) if mode != NoEval => true
                case _ => false
              }
            }
          }
        }
      }
    }
  }

  private def buildStats(): Unit = {
    val oneHAgo = System.currentTimeMillis() - 3600 * 1000
    val schoolQ = Schools.map(_.school.trim.getOrElse("": Rep[String]))
      .groupBy(x => x.toUpperCase).map(x => (x._1, x._2.length)).sortBy(_._1).result
    db.run(schoolQ).foreach { rawSchools =>
      db.run(Users.length.result).foreach { userCount: Int =>
        db.run((for {
          r <- Testruns if r.result =!= (Queued: org.ieee_passau.models.Result)
          s <- r.solution
        } yield (r.vm, r.completed, s.languageId)).result).foreach { rawJobs =>
          val jobs = rawJobs.view.flatMap {
            case job if job._1.getOrElse("").contains(" ") =>
              job._1.get.split(" ").zipWithIndex.map {
                case (vmName, idx) =>
                  if (idx == 0) (vmName, job._2, job._3)
                  else (vmName, job._2, "BINARY")
              }.toList
            case job => List((job._1.get, job._2, job._3))
          }

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

          val counts = (userCount, ranking(true).keySet.size)
          val schools = rawSchools.view.map { case (name, count) =>
            (name.toLowerCase.split(Array(' ', '-')).map(_.capitalize).mkString(" "), count)
          }.groupBy(_._1).map {
            case (name, cs) => (name, cs.map(_._2).sum)
          }.toList.sorted

          stats = StatsM(numJobs1H, vmRank1H, langRank1H, numJobsFull, vmRankFull, langRankFull, counts, schools)
        }
      }
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    this.tickSchedule.cancel()
  }

  override def receive: Receive = {
    case UpdateRankingM =>
      buildCache(true)
      buildCache(false)
      buildStats()

    case ProblemsQ(uid, maybeLang) =>
      val now = new Date()

      db.run(Users.byId(uid).result.headOption).map { sessionUser =>

        val lang = sessionUser.map(_.lang).orElse(maybeLang).orElse(Some(LanguageHelper.defaultLanguage)).get
        val problemList = for {
          p <- Problems if (p.readableStart < (now: Date) && p.readableStop > (now: Date)) || sessionUser.fold(false)(_.hidden)
        } yield (p.id, p.door, p.evalMode, p.points)

        ProblemTranslations.problemTitleListByLang(lang).flatMap { transList =>
          db.run(problemList.result).map { list =>
            list.map {
              case (id, door, mode, points) =>
                val submissionCounts = problemCounts(sessionUser.fold(false)(_.hidden))(id)
                ProblemInfo(id,
                  door,
                  transList.getOrElse(id, ""),
                  (calculateMaxProblemPoints(mode, points) _).tupled(submissionCounts).floor.toInt ,
                  sessionUser.map(u => ranking(u.hidden)(u.id.get)(id)._2.floor.toInt).getOrElse(0),
                  mode,
                  // Variant with user tires
                  //sessionUser.map(u => userXsubmissions(u.id.get)._2(id)).getOrElse(0),
                  submissionCounts._3,
                  submissionCounts._1,
                  sessionUser.fold(false)(u => ranking(u.hidden)(u.id.get)(id)._3)
                )
            }.sortBy(_.door)
          }
        }
      } pipeTo sender

    case RankingQ(uid) =>
      def simplify[A <: Entity[A]](query: PostgresProfile.StreamingProfileAction[scala.Seq[A], A, Effect.Read]) = {
        db.run(query).map(l => l.groupBy(u => u.id.get).map { case (id, u) => (id, u.head) })
      }

      val localPtr = userCorrectSolutions
      simplify(Users.to[List].result).map { users =>
        val sessionUserIsHidden = users.get(uid).fold(false)(_.hidden)
        val rank = ranking(sessionUserIsHidden).map { case (u, pl) =>
          val user = users(u)
          (pl.foldLeft(0d) { case (points: Double, (_: Int, (_: Int, actualPoints: Double, _: Boolean, _: EvalMode))) =>
            points + actualPoints
          }, user.username, user.hidden, user.id.get)
        }.toList.sortBy(-_._1 /*points*/).zipWithIndex.groupBy(_._1._1 /*points*/).flatMap {
          case (_, rankingPos: List[((Double, String, Boolean, Int), Int)]) => for {
            ((points: Double, username: String, isHidden: Boolean, userId: Int), _: Int) <- rankingPos
          } yield (rankingPos.head._2 + 1, username, isHidden, points.floor.toInt, localPtr(userId))
        }.toList.sortBy(x => (x._1, -x._5))
      } pipeTo sender

    case StatsQ =>
      sender ! stats
  }
}
