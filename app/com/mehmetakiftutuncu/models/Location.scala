package com.mehmetakiftutuncu.models

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.utilities.Log
import play.api.libs.json.{JsObject, JsValue, Json}

case class Location(latitude: Double, longitude: Double) extends ModelBase {
  override def toJson: JsObject = Location.toJson(this)
}

object Location extends LocationBase

trait LocationBase extends Jsonable[Location] {
  override def toJson(location: Location): JsObject = {
    Json.obj(
      "lat" -> location.latitude,
      "lon" -> location.longitude
    )
  }

  override def fromJson(json: JsValue): Either[Errors, Location] = {
    try {
      val latitudeAsOpt  = (json \ "lat").asOpt[Double]
      val longitudeAsOpt = (json \ "lon").asOpt[Double]

      val latitudeErrors = if (latitudeAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Latitude is missing!"))
      } else if (latitudeAsOpt.get < -90.0 || latitudeAsOpt.get > 90.0) {
        Errors(CommonError.invalidData.reason("Latitude must be in [-90.0, 90.0]!").data(latitudeAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val longitudeErrors = if (longitudeAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Longitude is missing!"))
      } else if (longitudeAsOpt.get < -180.0 || longitudeAsOpt.get > 180.0) {
        Errors(CommonError.invalidData.reason("Longitude must be in [-180.0, 180.0]!").data(longitudeAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val errors = latitudeErrors ++ longitudeErrors

      if (errors.nonEmpty) {
        Log.error("Location.fromJson", s"""Failed to create location from "$json"!""", errors)

        Left(errors)
      } else {
        val location = Location(latitudeAsOpt.get, longitudeAsOpt.get)

        Right(location)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData)

        Log.error(t, "Location.fromJson", s"""Failed to create location from "$json"!""")

        Left(errors)
    }
  }
}
