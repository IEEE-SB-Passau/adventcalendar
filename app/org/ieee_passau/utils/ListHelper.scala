package org.ieee_passau.utils

import java.util.Date

import org.ieee_passau.controllers.Beans.{SolutionListEntry, TestrunListEntry}
import org.ieee_passau.models._

import scala.collection.SeqView

object ListHelper {

  def buildSolutionList(solutionList: Seq[(Solution, Testcase, Testrun)]): SeqView[SolutionListEntry, Seq[_]] = {
    solutionList.view.groupBy(_._1).map { case (s, testruns) =>
      (s, testruns.map { case (_, testcase, testrun) =>
        TestrunListEntry(testcase.position, testcase, testrun)
      }.sortBy(_.position).toList)
    }.toList.view.sortBy(_._1.created)(Ordering[Date]).zipWithIndex.map { case ((solution, tre), pos) =>
      SolutionListEntry(pos + 1, solution, tre)
    }.sortBy(-_.position)
  }

  def reduceResult(list: Seq[(Result, Option[Int])]): Result = {
         if (list.forall { case (res, _) => res == Passed })                    Passed
    else if (list.exists { case (res, s) => res == Queued   || s.isDefined   }) Queued
    else if (list.forall { case (res, _) => res == Canceled || res == Passed }) Canceled
    else if (list.exists { case (res, _) => res == CompileError })              CompileError
    else if (list.exists { case (res, _) => res == ProgramError })              ProgramError
    else if (list.exists { case (res, _) => res == RuntimeExceeded })           RuntimeExceeded
    else if (list.exists { case (res, _) => res == MemoryExceeded })            MemoryExceeded
    else                                                                        WrongAnswer
  }
}
