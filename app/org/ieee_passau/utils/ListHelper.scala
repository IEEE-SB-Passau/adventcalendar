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
}
