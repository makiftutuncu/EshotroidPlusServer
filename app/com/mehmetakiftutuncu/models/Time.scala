package com.mehmetakiftutuncu.models

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.utilities.Log
import play.api.libs.json.{Json, JsValue, JsObject}

case class Time(busId: Int, dayType: DayType, direction: Direction, hour: Int, minute: Int) extends ModelBase {
  override def toJson: JsObject = Time.toJson(this)

  def toTimeString: String = f"$hour%02d:$minute%02d"
}

object Time extends TimeBase

trait TimeBase extends Jsonable[Time] {
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

        Log.error(t, "Time.fromJson", s"""Failed to create time from "$json"!""", errors)

        Left(errors)
    }
  }
}
