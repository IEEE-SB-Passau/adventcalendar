package org.ieee_passau.utils

import java.sql.SQLException

import play.api.Logger
import slick.dbio.Effect.Transactional
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.TransactionIsolation.RepeatableRead

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object DbHelper {
  def retry[T, E <: Effect](action:  DBIOAction[T, NoStream, E], tries: Int = 3)(implicit db: Database, ec: ExecutionContext, log: Logger): Future[T] = {
    db.run(retry0(action, tries))
  }

  private def retry0[T, E <: Effect](action:  DBIOAction[T, NoStream, E], tries: Int)(implicit db: Database, ec: ExecutionContext, log: Logger): DBIOAction[T, NoStream, E with Transactional] = {
    action.transactionally.withTransactionIsolation(RepeatableRead).asTry.flatMap {
      case Failure(t: Throwable) =>
        tries match {
          case 0 =>
            log.error("Error writing to database.", t)
            DBIO.failed(new SQLException("Could not write to database after 3 retries."))
          case _ => retry0[T, E](action, tries - 1)
        }
      case Success(x) =>
        log.debug("Update successful")
        DBIO.successful(x)
    }
  }
}
