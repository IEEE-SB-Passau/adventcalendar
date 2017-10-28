package org.ieee_passau.utils

import java.util.Date

import org.ieee_passau.controllers.Beans.{SolutionListEntry, TestrunListEntry}
import org.ieee_passau.models._

object ListHelper {

  def buildSolutionList(solutionList: List[(Solution, Testcase, Testrun)]): List[SolutionListEntry] = {

    solutionList.view.groupBy(_._1).map { case (s, testruns) =>
      (s, testruns.map {
        case (solution, testcase, testrun) => (testcase, testrun)
      }.sortBy(_._1.position))
    }.toList.view.sortBy(_._1.created)(Ordering[Date])
      .zipWithIndex.map {
      case ((solution, caseruns), pos) =>
        val solved = caseruns.forall { case (testcase, testrun) => testrun.result == Passed }
        val failed = caseruns.exists { case (testcase, testrun) => testrun.result != Passed && testrun.result != Queued }
        val canceled = caseruns.forall { case (testcase, testrun) => testrun.result == Canceled || testrun.result == Passed }
        val queued = caseruns.exists { case (testcase, testrun) => testrun.result == Queued || testrun.stage.isDefined}
        val result = if (queued)
          Queued
        else if (canceled && !solved)
          Canceled
        else if (failed)
          WrongAnswer
        else
          Passed

        SolutionListEntry(pos + 1, result, solution,
          caseruns.map { case (tcase, trun) =>
            TestrunListEntry(tcase.position, tcase, trun)
          }.sortBy(_.position).toList
        )
    }.sortBy(-_.position).toList
  }
}
