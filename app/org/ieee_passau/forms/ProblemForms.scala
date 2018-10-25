package org.ieee_passau.forms

import org.ieee_passau.controllers.Beans.TicketText
import org.ieee_passau.models.{EvalMode, Problem, ProblemTranslation, Problems}
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formats._
import play.api.i18n.Lang

object ProblemForms {
  val problemForm = Form(
    mapping(
      "id" -> optional(text),
      "title" -> nonEmptyText,
      "door" -> number, //(1, 24)
      "description" -> text,
      "readableStart" -> date("dd.MM.yyyy HH:mm"),
      "readableStop" -> date("dd.MM.yyyy HH:mm"),
      "solvableStart" -> date("dd.MM.yyyy HH:mm"),
      "solvableStop" -> date("dd.MM.yyyy HH:mm"),
      "evalMode" -> nonEmptyText,
      "cpuFactor" -> of[Float],
      "memFactor" -> of[Float]
    )
    ((id: Option[String], title, door, description, readableStart, readableStop, solvableStart, solvableStop,
      evalMode: String, cpuFacotr: Float, memFator: Float) =>
      Problem(if (id.isDefined) Some(id.get.toInt) else None, title, door, description, readableStart, readableStop,
        solvableStart, solvableStop, EvalMode(evalMode), cpuFacotr, memFator))
    ((p: Problem) => Some(Some(p.id.toString), p.title, p.door, p.description, p.readableStart, p.readableStop,
      p.solvableStart, p.solvableStop, p.evalMode.mode, p.cpuFactor, p.memFactor))

      verifying("problem.create.error.door", p =>
          if (p.id.isDefined) Problems.doorAvailable(p.door, p.id.get) else Problems.doorAvailable(p.door))
      verifying("viserror.date.reverse", p => p.readableStart.compareTo(p.readableStop) < 0)
      verifying("sloverror.date.reverse", p => p.solvableStart.compareTo(p.solvableStop) < 0)
  )

  val problemTranslationForm = Form(
    mapping(
      "problemId" -> number,
      "language" -> nonEmptyText,
      "title" -> nonEmptyText,
      "description" -> text
    )((problemId: Int, language: String, title: String, description: String)
    => ProblemTranslation(problemId, Lang(language), title, description))
    ((p: ProblemTranslation) => Some(p.problemId, p.language.code, p.title, p.description))

  )

  val ticketForm = Form(
    mapping(
      "text" -> nonEmptyText,
      "public" -> boolean
    )(TicketText.apply)(TicketText.unapply)
  )
}
