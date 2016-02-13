package com.mehmetakiftutuncu.models

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{ModelBase, Jsonable}
import com.mehmetakiftutuncu.utilities.Log
import play.api.libs.json.{JsObject, JsValue, Json}

case class Bus(id: Int, departure: String, arrival: String) extends ModelBase {
  override def toJson: JsObject = Bus.toJson(this)
}

object Bus extends BusBase

trait BusBase extends Jsonable[Bus] {
  override def toJson(bus: Bus): JsObject = {
    Json.obj(
      "id"        -> bus.id,
      "departure" -> bus.departure,
      "arrival"   -> bus.arrival
    )
  }

  override def fromJson(json: JsValue): Either[Errors, Bus] = {
    try {
      val idAsOpt        = (json \ "id").asOpt[Int]
      val departureAsOpt = (json \ "departure").asOpt[String]
      val arrivalAsOpt   = (json \ "arrival").asOpt[String]

      val idErrors = if (idAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Bus id is missing!"))
      } else if (idAsOpt.get <= 0) {
        Errors(CommonError.invalidData.reason("Bus id must be > 0!").data(idAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val departureErrors = if (departureAsOpt.getOrElse("").isEmpty) {
        Errors(CommonError.invalidData.reason("Bus departure area name is missing!"))
      } else {
        Errors.empty
      }

      val arrivalErrors = if (arrivalAsOpt.getOrElse("").isEmpty) {
        Errors(CommonError.invalidData.reason("Bus arrival area name is missing!"))
      } else {
        Errors.empty
      }

      val errors = idErrors ++ departureErrors ++ arrivalErrors

      if (errors.nonEmpty) {
        Log.error("Bus.fromJson", "Failed to create bus from Json!", errors)

        Left(errors)
      } else {
        val bus = Bus(idAsOpt.get, departureAsOpt.get, arrivalAsOpt.get)

        Right(bus)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData.data(json.toString()))

        Log.error(t, "Bus.fromJson", "Failed to create bus from Json!", errors)

        Left(errors)
    }
  }
}