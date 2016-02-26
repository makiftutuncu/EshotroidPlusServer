package com.mehmetakiftutuncu.models

import anorm.NamedParameter
import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.utilities.{DatabaseBase, Log}
import play.api.libs.json.{JsObject, JsValue, Json}

case class Time(busId: Int, dayType: DayType, direction: Direction, hour: Int, minute: Int) extends ModelBase {
  override def toJson: JsObject = Time.toJson(this)

  def toTimeString: String = f"$hour%02d:$minute%02d"
}

object Time extends TimeBase {
  override protected def Database: DatabaseBase = com.mehmetakiftutuncu.utilities.Database
}

trait TimeBase extends Jsonable[Time] {
  protected def Database: DatabaseBase

  def getTimesFromDB(busId: Int): Either[Errors, List[Time]] = {
    val sql = anorm.SQL("""SELECT * FROM Time WHERE busId = {busId} ORDER BY dayType, direction, hour, minute""").on(
      "busId" -> busId
    )

    try {
      Database.getMultiple(sql) match {
        case Left(getTimesErrors) =>
          Left(getTimesErrors)

        case Right(timeRows) =>
          val times: List[Time] = timeRows.map {
            row =>
              val busId           = row[Int]("Time.busId")
              val dayTypeString   = row[String]("Time.dayType")
              val directionString = row[String]("Time.direction")
              val hour            = row[Int]("Time.hour")
              val minute          = row[Int]("Time.minute")

              val dayType: DayType     = DayTypes.withName(dayTypeString)
              val direction: Direction = Directions.withName(directionString)

              Time(busId, dayType, direction, hour, minute)
          }

          Right(times)
      }
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Time.getTimesFromDB", s"""Failed to get times for bus "$busId" from DB!""")

        Left(errors)
    }
  }

  def saveTimesToDB(times: List[Time]): Errors = {
    val insert = """INSERT INTO Time (busId, dayType, direction, hour, minute) VALUES """

    val (values: List[String], parameters: List[NamedParameter]) = times.zipWithIndex.foldLeft(List.empty[String], List.empty[NamedParameter]) {
      case ((currentValues, currentParameters), (time, index)) =>
        val value = s"""({busId_$index}, {dayType_$index}, {direction_$index}, {hour_$index}, {minute_$index})"""

        val parameters = List(
          NamedParameter(s"busId_$index",     time.busId),
          NamedParameter(s"dayType_$index",   time.dayType.toString),
          NamedParameter(s"direction_$index", time.direction.toString),
          NamedParameter(s"hour_$index",      time.hour),
          NamedParameter(s"minute_$index",    time.minute)
        )

        (currentValues :+ value) -> (currentParameters ++ parameters)
    }

    val sql = anorm.SQL(insert + values.mkString(", ")).on(parameters:_*)

    try {
      Database.insert(sql)
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Time.saveTimesToDB", s"""Failed to save times "${Json.toJson(times.map(_.toJson))}" to DB!""")

        errors
    }
  }

  override def toJson(time: Time): JsObject = {
    Json.obj(
      "busId"     -> time.busId,
      "dayType"   -> time.dayType.toString,
      "direction" -> time.direction.toString,
      "hour"      -> time.hour,
      "minute"    -> time.minute
    )
  }

  override def fromJson(json: JsValue): Either[Errors, Time] = {
    try {
      val busIdAsOpt     = (json \ "busId").asOpt[Int]
      val dayTypeAsOpt   = (json \ "dayType").asOpt[String]
      val directionAsOpt = (json \ "direction").asOpt[String]
      val hourAsOpt      = (json \ "hour").asOpt[Int]
      val minuteAsOpt    = (json \ "minute").asOpt[Int]

      val busIdErrors = if (busIdAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Bus id is missing!"))
      } else if (busIdAsOpt.get <= 0) {
        Errors(CommonError.invalidData.reason("Bus id must be > 0!").data(busIdAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val dayTypeErrors = if (dayTypeAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Direction is missing!"))
      } else if (DayTypes.withNameOptional(dayTypeAsOpt.get).isEmpty) {
        Errors(CommonError.invalidData.reason("Direction is invalid!").data(dayTypeAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val directionErrors = if (directionAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Direction is missing!"))
      } else if (Directions.withNameOptional(directionAsOpt.get).isEmpty) {
        Errors(CommonError.invalidData.reason("Direction is invalid!").data(directionAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val hourErrors = if (hourAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Hour is missing!"))
      } else if (hourAsOpt.get < 0 || hourAsOpt.get > 23) {
        Errors(CommonError.invalidData.reason("Hour must be in [0, 23]!").data(hourAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val minuteErrors = if (minuteAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Minute is missing!"))
      } else if (minuteAsOpt.get < 0 || minuteAsOpt.get > 59) {
        Errors(CommonError.invalidData.reason("Minute must be in [0, 59]!").data(minuteAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val errors = busIdErrors ++ dayTypeErrors ++ directionErrors ++ hourErrors ++ minuteErrors

      if (errors.nonEmpty) {
        Log.error("Time.fromJson", s"""Failed to create time from "$json"!""", errors)

        Left(errors)
      } else {
        val time = Time(
          busId     = busIdAsOpt.get,
          dayType   = DayTypes.withName(dayTypeAsOpt.get),
          direction = Directions.withName(directionAsOpt.get),
          hour      = hourAsOpt.get,
          minute    = minuteAsOpt.get
        )

        Right(time)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData)

        Log.error(t, "Time.fromJson", s"""Failed to create time from "$json"!""")

        Left(errors)
    }
  }
}
