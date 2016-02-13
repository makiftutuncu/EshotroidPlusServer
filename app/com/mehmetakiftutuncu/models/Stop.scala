package com.mehmetakiftutuncu.models

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.utilities.Log
import play.api.libs.json.{Json, JsValue, JsObject}

case class Stop(id: Int, name: String, busId: Int, direction: Direction, location: Location) extends ModelBase {
  override def toJson: JsObject = Stop.toJson(this)

  def toJsonWithoutBusId: JsObject = Stop.toJson(this) - "busId"
}

object Stop extends StopBase {
  override def Location: LocationBase = com.mehmetakiftutuncu.models.Location
}

trait StopBase extends Jsonable[Stop] {
  protected def Location: LocationBase

  override def toJson(stop: Stop): JsObject = {
    Json.obj(
      "id"        -> stop.id,
      "name"      -> stop.name,
      "busId"     -> stop.busId,
      "direction" -> stop.direction.toString,
      "location"  -> stop.location.toJson
    )
  }

  override def fromJson(json: JsValue): Either[Errors, Stop] = {
    try {
      val idAsOpt          = (json \ "id").asOpt[Int]
      val nameAsOpt        = (json \ "name").asOpt[String]
      val busIdAsOpt       = (json \ "busId").asOpt[Int]
      val directionAsOpt   = (json \ "direction").asOpt[String]
      val errorsOrLocation = Location.fromJson((json \ "location").getOrElse(Json.obj()))

      val idErrors = if (idAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Stop id is missing!"))
      } else if (idAsOpt.get <= 0) {
        Errors(CommonError.invalidData.reason("Stop id must be > 0!").data(idAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val nameErrors = if (nameAsOpt.getOrElse("").isEmpty) {
        Errors(CommonError.invalidData.reason("Stop name is missing!"))
      } else {
        Errors.empty
      }

      val busIdErrors = if (busIdAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Stop bus id is missing!"))
      } else if (busIdAsOpt.get <= 0) {
        Errors(CommonError.invalidData.reason("Stop bus id must be > 0!").data(busIdAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val directionErrors = if (directionAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Stop direction is missing!"))
      } else if (Directions.withNameOptional(directionAsOpt.get).isEmpty) {
        Errors(CommonError.invalidData.reason("Stop direction is invalid!").data(directionAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val locationErrors = errorsOrLocation.left.toOption.getOrElse(Errors.empty)

      val errors = idErrors ++ nameErrors ++ busIdErrors ++ directionErrors ++ locationErrors

      if (errors.nonEmpty) {
        Log.error("Stop.fromJson", "Failed to create stop from Json!", errors)

        Left(errors)
      } else {
        val stop = Stop(idAsOpt.get, nameAsOpt.get, busIdAsOpt.get, Directions.withName(directionAsOpt.get), errorsOrLocation.right.get)

        Right(stop)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData.data(json.toString()))

        Log.error(t, "Stop.fromJson", "Failed to create stop from Json!", errors)

        Left(errors)
    }
  }
}
