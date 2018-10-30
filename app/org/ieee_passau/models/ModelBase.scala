package org.ieee_passau.models

import java.sql.Timestamp
import java.util.Date

import slick.ast.BaseTypedType
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcType

object DateSupport {
  implicit val dateMapper: JdbcType[Date] with BaseTypedType[Date] = MappedColumnType.base[Date, Timestamp] (
    d => new Timestamp(d.getTime),
    t => new Date(t.getTime)
  )

  implicit def dateOrdering: Ordering[Date] = Ordering.fromLessThan(_ before _)
}

abstract class TableWithId[T <: Entity[T]](tag: Tag, tablename: String) extends Table[T](tag, tablename) {
  implicit val dateMapper: JdbcType[Date] with BaseTypedType[Date] = DateSupport.dateMapper

  def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
}

trait Entity[E <: Entity[E]] {
  self: E => def id: Option[Int]
  def withId(id: Int): E
}
