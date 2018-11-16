package org.ieee_passau.utils

import scala.language.existentials
import slick.ast._
import slick.jdbc.PostgresProfile._
import slick.jdbc.PostgresProfile.api._
import slick.jdbc._
import slick.relational.{CompiledMapping, ResultConverter}
import slick.util.SQLBuilder

/**
  * Taken from https://gist.github.com/shiraeeshi/b3b875b35dc6564c07b0c9df47601501
  */
object UpdateReturning {

  // see https://stackoverflow.com/questions/30212785/slick-3-0-update-columns-in-a-table-and-return-whole-table-object/31545270#31545270

  implicit class UpdateReturningInvoker[E, U, C[_]](updateQuery: Query[E, U, C]) {
    def updateReturning[A, F](returningQuery: Query[A, F, C], v: U)(implicit db: Database) = {
      val ResultSetMapping(_,
      CompiledStatement(_, sres: SQLBuilder.Result, _),
      CompiledMapping(_updateConverter, _)) = updateCompiler.run(updateQuery.toNode).tree

      val returningNode = returningQuery.toNode
      val fieldNames = returningNode match {
        case Bind(_, _, Pure(Select(_, col), _)) =>
          List(col.name)
        case Bind(_, _, Pure(ProductNode(children), _)) =>
          children.map { case Select(_, col) => col.name }.toSeq
        case Bind(_, _, TableExpansion(_, _, TypeMapping(ProductNode(children), _, _))) =>
          children.map { case Select(_, col) => col.name }.toSeq

        case Pure(Select(_, col), _) =>
          List(col.name)
        case Pure(ProductNode(children), _) =>
          children.map { case Select(_, col) => col.name }.toSeq
        case TableExpansion(_, _, TypeMapping(ProductNode(children), _, _)) =>
          children.map {
            case Select(_, col) => col.name
            case OptionApply(col) =>
              col match {
                case Select(_, childCol) => childCol.name
              }
          }.toSeq
      }

      implicit val pconv: SetParameter[U] = {
        val ResultSetMapping(_, _, CompiledMapping(converter: ResultConverter[JdbcResultConverterDomain, U], _)) =
          updateCompiler.run(updateQuery.toNode).tree
        SetParameter[U] { (value, params) =>
          converter.set(value, params.ps)
        }
      }

      implicit val rconv: GetResult[F] = {
        val ResultSetMapping(_, _, CompiledMapping(converter: ResultConverter[JdbcResultConverterDomain, F], _)) =
          queryCompiler.run(returningNode).tree
        GetResult[F] { p => converter.read(p.rs) }
      }

      val fieldsExp = fieldNames.map(quoteIdentifier).mkString(", ")
      val pconvUnit = pconv.applied(v)
      val sql = sres.sql + s" RETURNING $fieldsExp"

      SQLActionBuilder(List(sql), pconvUnit).as[F]
    }
  }

}
