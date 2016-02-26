package com.mehmetakiftutuncu.utilities

import java.sql.Connection

import anorm.{Row, SimpleSql, SqlParser}
import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import play.api.Play.current
import play.api.db.DB

object Database extends DatabaseBase

trait DatabaseBase {
  val timeout = Conf.Database.timeoutInSeconds

  def getSingle(sql: SimpleSql[Row]): Either[Errors, Option[Row]] = {
    withConnection {
      implicit connection =>
        val errorListOrRowList = sql.withQueryTimeout(Option(timeout)).executeQuery().fold(List.empty[Row])(_ :+ _)

        errorListOrRowList match {
          case Left(errorList) =>
            val errors: Errors = Errors(errorList.map(t => CommonError.database.reason(t.getMessage)))

            Log.error("Database.getSingle", "Query failed!", errors)

            Left(errors)

          case Right(rowList) =>
            Right(rowList.headOption)
        }
    }
  }

  def getMultiple(sql: SimpleSql[Row]): Either[Errors, List[Row]] = {
    withConnection {
      implicit connection =>
        val errorListOrRowList = sql.withQueryTimeout(Option(timeout)).executeQuery().fold(List.empty[Row])(_ :+ _)

        errorListOrRowList match {
          case Left(errorList) =>
            val errors: Errors = Errors(errorList.map(t => CommonError.database.reason(t.getMessage)))

            Log.error("Database.getMultiple", "Query failed!", errors)

            Left(errors)

          case Right(rowList) => Right(rowList)
        }
    }
  }

  def insert(sql: SimpleSql[Row]): Errors = {
    withConnection {
      implicit connection =>
        sql.withQueryTimeout(Option(timeout)).executeInsert(SqlParser.scalar[Long].*)

        Errors.empty
    }
  }

  def update         = ???
  def insertOrUpdate = ???
  def delete         = ???
  def apply          = ???

  private def withConnection[R](action: Connection => R): R = {
    DB.withConnection(action)
  }
}
