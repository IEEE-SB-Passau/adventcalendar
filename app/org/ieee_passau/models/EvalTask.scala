package org.ieee_passau.models

import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{CompiledFunction, ForeignKeyQuery, ProvenShape}

case class EvalTask(id: Option[Int], problemId: Int, position: Int, command: String, filename: String, file: Array[Byte],
                    outputCheck: Boolean, scoreCalc: Boolean, useStdin: Boolean, useExpout: Boolean, useProgout: Boolean,
                    useProgram: Boolean, runCorrect: Boolean, runWrong: Boolean) extends Entity[EvalTask] {
  override def withId(id: Int): EvalTask = this.copy(id = Some(id))
}

class EvalTasks(tag: Tag) extends TableWithId[EvalTask](tag, "eval_task") {
  def problemId: Rep[Int] = column[Int]("problem_id")
  def position: Rep[Int] = column[Int]("position")
  def command: Rep[String] = column[String]("command")
  def filename: Rep[String] = column[String]("filename")
  def file: Rep[Array[Byte]] = column[Array[Byte]]("file")
  def outputCheck: Rep[Boolean] = column[Boolean]("output_check")
  def scoreCalc: Rep[Boolean] = column[Boolean]("score_calc")
  def useStdin: Rep[Boolean] = column[Boolean]("use_stdin")
  def useExpout: Rep[Boolean] = column[Boolean]("use_expout")
  def useProgout: Rep[Boolean] = column[Boolean]("use_prevout")
  def useProgram: Rep[Boolean] = column[Boolean]("use_prog")
  def runCorrect: Rep[Boolean] = column[Boolean]("run_on_correct_result")
  def runWrong: Rep[Boolean] = column[Boolean]("run_on_wrong_result")

  def problem: ForeignKeyQuery[Problems, Problem] = foreignKey("problem_fk", problemId, Problems)(_.id)

  override def * : ProvenShape[EvalTask] = (id.?, problemId, position, command, filename, file, outputCheck, scoreCalc,
    useStdin, useExpout, useProgout, useProgram, runCorrect, runWrong) <> (EvalTask.tupled, EvalTask.unapply)
}

object EvalTasks extends TableQuery(new EvalTasks(_)) {
  def byId: CompiledFunction[Rep[Int] => Query[EvalTasks, EvalTask, Seq], Rep[Int], Int, Query[EvalTasks, EvalTask, Seq], Seq[EvalTask]] =
    this.findBy(_.id)
  def update(id: Int, task: EvalTask): DBIOAction[Int, NoStream, Effect.Write] =
    this.byId(id).update(task.withId(id))
}
