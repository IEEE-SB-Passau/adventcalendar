package org.ieee_passau.models

import java.util.Date

import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{Session, _}

import scala.slick.lifted.{CompiledFunction, ProvenShape}

case class Problem (id: Option[Int], title: String, door: Int, description: String, readableStart: Date,
                    readableStop: Date, solvableStart: Date, solvableStop: Date, evalMode: EvalMode) extends Entity[Problem] {
  def withId(id: Int): Problem = this.copy(id = Some(id))
  def readable: Boolean = {
    val now = new Date()
    readableStart.before(now) && readableStop.after(now)
  }
  def solvable: Boolean = {
    val now = new Date()
    solvableStart.before(now) && solvableStop.after(now)
  }
}

class Problems(tag: Tag) extends TableWithId[Problem](tag, "problems") {
  def title: Column[String] = column[String]("title")
  def door: Column[Int] = column[Int]("door")
  def description: Column[String] = column[String]("description")
  def readableStart: Column[Date] = column[Date]("readable_start")
  def readableStop: Column[Date] = column[Date]("readable_stop")
  def solvableStart: Column[Date] = column[Date]("solvable_start")
  def solvableStop: Column[Date] = column[Date]("solvable_stop")
  def evalMode: Column[EvalMode] = column[EvalMode]("eval_mode") (EvalMode.evalModeTypeMapper)

  def * : ProvenShape[Problem] = (id.?, title, door, description, readableStart, readableStop, solvableStart, solvableStop, evalMode) <> (Problem.tupled, Problem.unapply)
}

object Problems extends TableQuery(new Problems(_)) {
  def byDoor: CompiledFunction[(Column[Int]) => Query[Problems, Problem, Seq], Column[Int], Int, Query[Problems, Problem, Seq], Seq[Problem]] = this.findBy(_.door)
  def byId: CompiledFunction[(Column[Int]) => Query[Problems, Problem, Seq], Column[Int], Int, Query[Problems, Problem, Seq], Seq[Problem]] = this.findBy(_.id)
  def update(id: Int, problem: Problem)(implicit session: Session): Int = this.filter(_.id === id).update(problem.withId(id))

  def doorAvailable(door: Int, id: Int): Boolean = {
    DB.withSession { implicit session: Session =>
      !Problems.filter(_.door === door).list.exists(p => p.id.get != id)
    }
  }

  def doorAvailable(door: Int): Boolean = {
    DB.withSession { implicit session: Session =>
      Query(Problems.filter(_.door === door).length).first == 0
    }
  }
}