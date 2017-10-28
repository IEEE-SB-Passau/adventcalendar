package org.ieee_passau.models

import play.api.db.slick.Config.driver.simple._

import scala.slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape}

case class EvalTask(id: Option[Int], problemId: Int, position: Int, command: String, filename: String, file: Array[Byte],
                    outputCheck: Boolean, scoreCalc: Boolean, useStdin: Boolean, useExpout: Boolean, useProgout: Boolean,
                    useProgram: Boolean, runCorrect: Boolean, runWrong: Boolean) extends Entity[EvalTask] {
  override def withId(id: Int): EvalTask = this.copy(id = Some(id))
}

class EvalTasks(tag: Tag) extends TableWithId[EvalTask](tag, "eval_task") {
  def problemId: Column[Int] = column[Int]("problem_id")
  def position: Column[Int] = column[Int]("position")
  def command: Column[String] = column[String]("command")
  def filename: Column[String] = column[String]("filename")
  def file: Column[Array[Byte]] = column[Array[Byte]]("file")
  def outputCheck: Column[Boolean] = column[Boolean]("output_check")
  def scoreCalc: Column[Boolean] = column[Boolean]("score_calc")
  def useStdin: Column[Boolean] = column[Boolean]("use_stdin")
  def useExpout: Column[Boolean] = column[Boolean]("use_expout")
  def useProgout: Column[Boolean] = column[Boolean]("use_prevout")
  def useProgram: Column[Boolean] = column[Boolean]("use_prog")
  def runCorrect: Column[Boolean] = column[Boolean]("run_on_correct_result")
  def runWrong: Column[Boolean] = column[Boolean]("run_on_wrong_result")

  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id)

  override def * : ProvenShape[EvalTask] = (id.?, problemId, position, command, filename, file, outputCheck, scoreCalc,
    useStdin, useExpout, useProgout, useProgram, runCorrect, runWrong) <> (EvalTask.tupled, EvalTask.unapply)
}

object EvalTasks extends TableQuery(new EvalTasks(_)) {
  def byProblemId: CompiledFunction[(Column[Int]) => Query[EvalTasks, EvalTask, Seq], Column[Int], Int, Query[EvalTasks, EvalTask, Seq], Seq[EvalTask]] =
    this.findBy(_.problemId)
  def byId: CompiledFunction[(Column[Int]) => Query[EvalTasks, EvalTask, Seq], Column[Int], Int, Query[EvalTasks, EvalTask, Seq], Seq[EvalTask]] =
    this.findBy(_.id)
  def update(id: Int, evalTask: EvalTask)(implicit session: Session): Int =
    this.filter(_.id === id).update(evalTask.withId(id))
}
