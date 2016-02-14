package com.mehmetakiftutuncu.utilities

import java.sql.Connection

import anorm.{Row, SimpleSql}
import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import play.api.Play.current
import play.api.db.DB

object Database extends DatabaseBase

trait DatabaseBase {
  val timeout = Conf.Database.timeoutInSeconds

  def getSingle = ???

  def getMultiple(sql: SimpleSql[Row]): Either[Errors, List[Row]] = {
    withConnection {
      implicit connection =>
        val errorListOrRowList = sql.withQueryTimeout(Option(timeout)).executeQuery().fold(List.empty[Row])(_ :+ _)

        errorListOrRowList match {
          case Left(errorList) => Left(
            Errors(
              errorList.map {
                throwable =>
                  Log.error(throwable, "Database.getMultiple", "Query failed!")

                  CommonError.database.reason(throwable.getMessage)
              }
            )
          )

          case Right(rowList) => Right(rowList)
        }
    }
  }

  def insert(sql: SimpleSql[Row]): Errors = {
    withConnection {
      implicit connection =>
        sql.withQueryTimeout(Option(timeout)).executeInsert()

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
