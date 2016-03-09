package com.mehmetakiftutuncu.models

import anorm.NamedParameter
import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.utilities.{DatabaseBase, Log}
import play.api.libs.json.{JsObject, JsValue, Json}

case class Stop(id: Int, name: String, busId: Int, direction: Direction, location: Location) extends ModelBase {
  override def toJson: JsObject = Stop.toJson(this)
}

object Stop extends StopBase {
  override protected def Database: DatabaseBase = com.mehmetakiftutuncu.utilities.Database
}

trait StopBase extends Jsonable[Stop] {
  protected def Database: DatabaseBase

  def getStopsFromDB(busId: Int): Either[Errors, List[Stop]] = {
    val sql = anorm.SQL("""SELECT * FROM Stop WHERE busId = {busId} ORDER BY id""").on(
      "busId" -> busId
    )

    try {
      Database.getMultiple(sql) match {
        case Left(getStopsErrors) =>
          Left(getStopsErrors)

        case Right(stopRows) =>
          val stops: List[Stop] = stopRows.map {
            row =>
              val id        = row[Int]("Stop.id")
              val name      = row[String]("Stop.name")
              val direction = Directions.withName(row[String]("Stop.direction"))
              val latitude  = row[Double]("Stop.latitude")
              val longitude = row[Double]("Stop.longitude")

              Stop(id, name, busId, direction, Location(latitude, longitude))
          }

          Right(stops)
      }
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Stop.getStopsFromDB", s"Failed to get stops for bus $busId from DB!")

        Left(errors)
    }
  }

  def saveStopsToDB(stops: List[Stop]): Errors = {
    val insertPart = """INSERT INTO Stop (id, name, busId, direction, latitude, longitude) VALUES """

    val (values: List[String], parameters: List[NamedParameter]) = stops.zipWithIndex.foldLeft(List.empty[String], List.empty[NamedParameter]) {
      case ((currentValues, currentParameters), (stop, index)) =>
        val value = s"""({id_$index}, {name_$index}, {busId_$index}, {direction_$index}, {latitude_$index}, {longitude_$index})"""

        val parameters = List(
          NamedParameter(s"id_$index",        stop.id),
          NamedParameter(s"name_$index",      stop.name),
          NamedParameter(s"busId_$index",     stop.busId),
          NamedParameter(s"direction_$index", stop.direction.toString),
          NamedParameter(s"latitude_$index",  stop.location.latitude),
          NamedParameter(s"longitude_$index", stop.location.longitude)
        )

        (currentValues :+ value) -> (currentParameters ++ parameters)
    }

    val deleteSQL = anorm.SQL(s"""DELETE FROM Stop WHERE busId IN (${stops.map(_.busId).toSet.mkString(", ")})""")
    val insertSQL = anorm.SQL(insertPart + values.mkString(", ")).on(parameters:_*)

    try {
      Database.insert(insertSQL, Option(deleteSQL))
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Stop.saveStopsToDB", s"""Failed to save stops "${Json.toJson(stops.map(_.toJson))}" to DB!""")

        errors
    }
  }

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
        Errors(CommonError.invalidData.reason("Id is missing!"))
      } else if (idAsOpt.get <= 0) {
        Errors(CommonError.invalidData.reason("Id must be > 0!").data(idAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val nameErrors = if (nameAsOpt.getOrElse("").isEmpty) {
        Errors(CommonError.invalidData.reason("Name is missing!"))
      } else {
        Errors.empty
      }

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

      val locationErrors = errorsOrLocation.left.toOption.getOrElse(Errors.empty)

      val errors = idErrors ++ nameErrors ++ busIdErrors ++ directionErrors ++ locationErrors

      if (errors.nonEmpty) {
        Log.error("Stop.fromJson", s"""Failed to create stop from "$json"!""", errors)

        Left(errors)
      } else {
        val stop = Stop(
          id        = idAsOpt.get,
          name      = nameAsOpt.get,
          busId     = busIdAsOpt.get,
          direction = Directions.withName(directionAsOpt.get),
          location  = errorsOrLocation.right.get
        )

        Right(stop)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData)

        Log.error(t, "Stop.fromJson", s"""Failed to create stop from "$json"!""")

        Left(errors)
    }
  }
}
