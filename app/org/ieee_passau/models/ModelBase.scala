package org.ieee_passau.models

import java.sql.Timestamp
import java.util.Date

import play.api.db.slick.Config.driver.simple._

import scala.slick.ast.BaseTypedType
import scala.slick.jdbc.JdbcType

object DateSupport {
  implicit val dateMapper: JdbcType[Date] with BaseTypedType[Date] = MappedColumnType.base[Date, Timestamp] (
    d => new Timestamp(d.getTime),
    t => new Date(t.getTime)
  )
}
abstract class TableWithId[T <: Entity[T]](tag: Tag, tablename: String) extends Table[T](tag, tablename) {
  implicit val dateMapper: JdbcType[Date] with BaseTypedType[Date] = DateSupport.dateMapper

  def id: Column[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
}

trait Entity[E <: Entity[E]] {
  self: E => def id: Option[Int]
  def withId(id: Int): E
}
