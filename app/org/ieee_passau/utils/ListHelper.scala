package org.ieee_passau.utils

import java.util.Date

import org.ieee_passau.controllers.Beans.{SolutionListEntry, TestrunListEntry}
import org.ieee_passau.models._

object ListHelper {

  def buildSolutionList(solutionList: List[(Solution, Testcase, Testrun)]): List[SolutionListEntry] = {

    solutionList.view.groupBy(_._1).map { case (s, testruns) =>
      (s, testruns.map {
        case (_, testcase, testrun) => (testcase, testrun)
      }.sortBy(_._1.position))
    }.toList.view.sortBy(_._1.created)(Ordering[Date])
      .zipWithIndex.map {
      case ((solution, caseruns), pos) =>

        val result = if (
          caseruns.forall { case (_, testrun) => testrun.result == Passed })
          Passed
        else if (
          caseruns.exists { case (_, testrun) => testrun.result == Queued || testrun.stage.isDefined })
          Queued
        else if (
          caseruns.forall { case (_, testrun) => testrun.result == Canceled || testrun.result == Passed })
          Canceled
        else if(
          caseruns.exists { case (_, testrun) => testrun.result == CompileError })
          CompileError
        else if(
          caseruns.exists { case (_, testrun) => testrun.result == ProgramError })
          ProgramError
        else if(
          caseruns.exists { case (_, testrun) => testrun.result == RuntimeExceeded })
          RuntimeExceeded
        else if(
          caseruns.exists { case (_, testrun) => testrun.result == MemoryExceeded })
          MemoryExceeded
        else
          WrongAnswer

        SolutionListEntry(pos + 1, result, solution,
          caseruns.map { case (tcase, trun) =>
            TestrunListEntry(tcase.position, tcase, trun)
          }.sortBy(_.position).toList
        )
    }.sortBy(-_.position).toList
  }
}
