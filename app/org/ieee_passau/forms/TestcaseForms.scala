package org.ieee_passau.forms

import org.ieee_passau.models.{EvalTask, Testcase, Visibility}
import play.api.data.Forms._
import play.api.data._

object TestcaseForms {
  val testcaseForm = Form(
    mapping(
      "id" -> optional(number),
      "problemId" -> number,
      "position" -> number, // TODO check uniqueness for problem
      "visibility" -> text,
      "input" -> text,
      "output" -> text,
      "points" -> number
    )((id: Option[Int], problemId: Int, position: Int, visibility: String, input: String, output: String, points: Int)
        => Testcase(id, problemId, position, Visibility(visibility), input, output, points))
    ((t: Testcase) => Some(t.id, t.problemId, t.position, t.visibility.scope, t.input, t.expectedOutput, t.points))
  )

  val evalTaskForm = Form(
    mapping(
      "id" -> optional(number),
      "problemId" -> number,
      "position" -> number, // TODO check uniqueness for problem
      "command" -> text,
      "outputCheck" -> boolean,
      "scoreCalc" -> boolean,
      "runCorrect" -> boolean,
      "runWrong" -> boolean
    )
    ((id: Option[Int], problemId: Int, position: Int, command: String, outputCheck: Boolean, scoreCalc: Boolean,
        runCorrect: Boolean, runWrong: Boolean) =>
      EvalTask(id, problemId, position, command, "", Array(), outputCheck, scoreCalc, command.contains("{stdIn}"),
        command.contains("{expOut}"), command.contains("{progOut}"), command.contains("{program}"), runCorrect, runWrong))
    ((t: EvalTask) => Some(t.id, t.problemId, t.position, t.command, t.outputCheck, t.scoreCalc, t.runCorrect, t.runWrong))
  )
}
