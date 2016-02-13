package com.mehmetakiftutuncu.models

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.utilities.Log
import play.api.libs.json.{Json, JsValue, JsObject}

case class Time(hour: Int, minute: Int) extends ModelBase {
  override def toJson: JsObject = Time.toJson(this)
}

object Time extends TimeBase

trait TimeBase extends Jsonable[Time] {
  override def toJson(time: Time): JsObject = {
    Json.obj(
      "hour"   -> time.hour,
      "minute" -> time.minute
    )
  }

  override def fromJson(json: JsValue): Either[Errors, Time] = {
    try {
      val hourAsOpt   = (json \ "hour").asOpt[Int]
      val minuteAsOpt = (json \ "minute").asOpt[Int]

      val hourErrors = if (hourAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Time hour is missing!"))
      } else if (hourAsOpt.get < 0 || hourAsOpt.get > 23) {
        Errors(CommonError.invalidData.reason("Time hour must be in [0, 23]!").data(hourAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val minuteErrors = if (minuteAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Time minute is missing!"))
      } else if (minuteAsOpt.get < 0 || minuteAsOpt.get > 59) {
        Errors(CommonError.invalidData.reason("Time minute must be in [0, 59]!").data(minuteAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val errors = hourErrors ++ minuteErrors

      if (errors.nonEmpty) {
        Log.error("Time.fromJson", "Failed to create time from Json!", errors)

        Left(errors)
      } else {
        val time = Time(hourAsOpt.get, minuteAsOpt.get)

        Right(time)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData.data(json.toString()))

        Log.error(t, "Time.fromJson", "Failed to create time from Json!", errors)

        Left(errors)
    }
  }
}
