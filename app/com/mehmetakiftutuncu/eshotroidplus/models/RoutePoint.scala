package com.mehmetakiftutuncu.eshotroidplus.models

import anorm.NamedParameter
import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.eshotroidplus.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.eshotroidplus.utilities.{DatabaseBase, Log}
import play.api.libs.json.{JsObject, JsValue, Json}

case class RoutePoint(busId: Int, direction: Direction, description: String, location: Location) extends ModelBase {
  override def toJson: JsObject = RoutePoint.toJson(this)
}

object RoutePoint extends RoutePointBase {
  override protected def Database: DatabaseBase = com.mehmetakiftutuncu.eshotroidplus.utilities.Database
}

trait RoutePointBase extends Jsonable[RoutePoint] {
  protected def Database: DatabaseBase

  def getRoutePointsFromDB(busId: Int): Either[Errors, List[RoutePoint]] = {
    val sql = anorm.SQL("""SELECT * FROM RoutePoint WHERE busId = {busId} ORDER BY direction, latitude, longitude""").on(
      "busId" -> busId
    )

    try {
      Database.getMultiple(sql) match {
        case Left(getRouteErrors) =>
          Left(getRouteErrors)

        case Right(routeRows) =>
          val routePoints: List[RoutePoint] = routeRows.map {
            row =>
              val direction   = Directions.withName(row[String]("RoutePoint.direction"))
              val description = row[String]("RoutePoint.description")
              val latitude    = row[Double]("RoutePoint.latitude")
              val longitude   = row[Double]("RoutePoint.longitude")

              RoutePoint(busId, direction, description, Location(latitude, longitude))
          }

          Right(routePoints)
      }
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "RoutePoint.getRoutePointsFromDB", s"Failed to get route points for bus $busId from DB!")

        Left(errors)
    }
  }

  def saveRoutePointsToDB(routePoints: List[RoutePoint]): Errors = {
    val insertPart = """INSERT INTO RoutePoint (busId, direction, description, latitude, longitude) VALUES """

    val (values: List[String], parameters: List[NamedParameter]) = routePoints.zipWithIndex.foldLeft(List.empty[String], List.empty[NamedParameter]) {
      case ((currentValues, currentParameters), (routePoint, index)) =>
        val value = s"""({busId_$index}, {direction_$index}, {description_$index}, {latitude_$index}, {longitude_$index})"""

        val parameters = List(
          NamedParameter(s"busId_$index",       routePoint.busId),
          NamedParameter(s"direction_$index",   routePoint.direction.toString),
          NamedParameter(s"description_$index", routePoint.description),
          NamedParameter(s"latitude_$index",    routePoint.location.latitude),
          NamedParameter(s"longitude_$index",   routePoint.location.longitude)
        )

        (currentValues :+ value) -> (currentParameters ++ parameters)
    }

    val deleteSQL = anorm.SQL(s"""DELETE FROM RoutePoint WHERE busId IN (${routePoints.map(_.busId).toSet.mkString(", ")})""")
    val insertSQL = anorm.SQL(insertPart + values.mkString(", ")).on(parameters:_*)

    try {
      Database.insert(insertSQL, Option(deleteSQL))
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "RoutePoint.saveRoutePointsToDB", s"""Failed to save route points "${Json.toJson(routePoints.map(_.toJson))}" to DB!""")

        errors
    }
  }

  override def toJson(routePoint: RoutePoint): JsObject = {
    Json.obj(
      "busId"       -> routePoint.busId,
      "direction"   -> routePoint.direction.toString,
      "description" -> routePoint.description,
      "location"    -> routePoint.location.toJson
    )
  }

  override def fromJson(json: JsValue): Either[Errors, RoutePoint] = {
    try {
      val busIdAsOpt       = (json \ "busId").asOpt[Int]
      val directionAsOpt   = (json \ "direction").asOpt[String]
      val descriptionAsOpt = (json \ "description").asOpt[String]
      val errorsOrLocation = Location.fromJson((json \ "location").getOrElse(Json.obj()))

      val busIdErrors = if (busIdAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Bus id is missing!"))
      } else if (busIdAsOpt.get <= 0) {
        Errors(CommonError.invalidData.reason("Bus id must be > 0!").data(busIdAsOpt.get.toString))
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

      val descriptionErrors = if (descriptionAsOpt.isEmpty) {
        Errors(CommonError.invalidData.reason("Description is missing!"))
      } else {
        Errors.empty
      }

      val locationErrors = errorsOrLocation.left.toOption.getOrElse(Errors.empty)

      val errors = busIdErrors ++ directionErrors ++ descriptionErrors ++ locationErrors

      if (errors.nonEmpty) {
        Log.error("RoutePoint.fromJson", s"""Failed to create route point from "$json"!""", errors)

        Left(errors)
      } else {
        val stop = RoutePoint(
          busId       = busIdAsOpt.get,
          direction   = Directions.withName(directionAsOpt.get),
          description = descriptionAsOpt.get,
          location    = errorsOrLocation.right.get
        )

        Right(stop)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData)

        Log.error(t, "RoutePoint.fromJson", s"""Failed to create route point from "$json"!""")

        Left(errors)
    }
  }
}
