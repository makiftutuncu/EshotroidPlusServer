package com.mehmetakiftutuncu.models

import anorm.NamedParameter
import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.base.{Jsonable, ModelBase}
import com.mehmetakiftutuncu.utilities.{DatabaseBase, Log}
import play.api.libs.json.{JsObject, JsValue, Json}

case class Bus(id: Int, departure: String, arrival: String) extends ModelBase {
  override def toJson: JsObject = Bus.toJson(this)
}

object Bus extends BusBase {
  override protected def Database: DatabaseBase = com.mehmetakiftutuncu.utilities.Database
}

trait BusBase extends Jsonable[Bus] {
  protected def Database: DatabaseBase

  def getBusListFromDB: Either[Errors, List[Bus]] = {
    val sql = anorm.SQL("""SELECT * FROM Bus ORDER BY id""")

    try {
      Database.getMultiple(sql) match {
        case Left(getBusListErrors) => Left(getBusListErrors)

        case Right(busListRows) =>
          val busList: List[Bus] = busListRows.map {
            row =>
              val id        = row[Int]("Bus.id")
              val departure = row[String]("Bus.departure")
              val arrival   = row[String]("Bus.arrival")

              Bus(id, departure, arrival)
          }

          Right(busList)
      }
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Bus.getBusListFromDB", "Failed to get bus list from DB!")

        Left(errors)
    }
  }

  def saveBusListToDB(busList: List[Bus]): Errors = {
    val insertPart = """INSERT INTO Bus (id, departure, arrival) VALUES """

    val (values: List[String], parameters: List[NamedParameter]) = busList.zipWithIndex.foldLeft(List.empty[String], List.empty[NamedParameter]) {
      case ((currentValues, currentParameters), (bus, index)) =>
        val value = s"""({id_$index}, {departure_$index}, {arrival_$index})"""

        val parameters = List(
          NamedParameter(s"id_$index", bus.id),
          NamedParameter(s"departure_$index", bus.departure),
          NamedParameter(s"arrival_$index", bus.arrival)
        )

        (currentValues :+ value) -> (currentParameters ++ parameters)
    }

    val deleteSQL = anorm.SQL("""DELETE FROM Bus""")
    val insertSQL = anorm.SQL(insertPart + values.mkString(", ")).on(parameters:_*)

    try {
      Database.insert(insertSQL, Option(deleteSQL))
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Bus.saveBusListToDB", s"""Failed to save bus list "${Json.toJson(busList.map(_.toJson))}" to DB!""")

        errors
    }
  }

  def getBusFromDB(id: Int): Either[Errors, Option[Bus]] = {
    val sql = anorm.SQL("""SELECT * FROM Bus WHERE id = {id}""").on("id" -> id)

    try {
      Database.getSingle(sql) match {
        case Left(getBusListErrors) =>
          Left(getBusListErrors)

        case Right(rowAsOpt) =>
          val busAsOpt = rowAsOpt.map {
            row =>
              val id        = row[Int]("Bus.id")
              val departure = row[String]("Bus.departure")
              val arrival   = row[String]("Bus.arrival")

              Bus(id, departure, arrival)
          }

          Right(busAsOpt)
      }
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Bus.getBusFromDB", s"Failed to get bus $id from DB!")

        Left(errors)
    }
  }

  def saveBusToDB(bus: Bus): Errors = {
    val deleteSQL = anorm.SQL("""DELETE FROM Bus WHERE id = {id}""").on("id" -> bus.id)
    val insertSQL = anorm.SQL(
      """
        |INSERT INTO Bus (id, departure, arrival) VALUES
        |({id}, {departure}, {arrival})
      """.stripMargin
    ).on("id" -> bus.id, "departure" -> bus.departure, "arrival" -> bus.arrival)

    try {
      Database.insert(insertSQL, Option(deleteSQL))
    } catch {
      case t: Throwable =>
        val errors: Errors = Errors(CommonError.database)

        Log.error(t, "Bus.saveBusToDB", s"Failed to save bus $bus to DB!")

        errors
    }
  }

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
        Errors(CommonError.invalidData.reason("Id is missing!"))
      } else if (idAsOpt.get <= 0) {
        Errors(CommonError.invalidData.reason("Id must be > 0!").data(idAsOpt.get.toString))
      } else {
        Errors.empty
      }

      val departureErrors = if (departureAsOpt.getOrElse("").isEmpty) {
        Errors(CommonError.invalidData.reason("Departure is missing!"))
      } else {
        Errors.empty
      }

      val arrivalErrors = if (arrivalAsOpt.getOrElse("").isEmpty) {
        Errors(CommonError.invalidData.reason("Arrival is missing!"))
      } else {
        Errors.empty
      }

      val errors = idErrors ++ departureErrors ++ arrivalErrors

      if (errors.nonEmpty) {
        Log.error("Bus.fromJson", s"""Failed to create bus from "$json"!""", errors)

        Left(errors)
      } else {
        val bus = Bus(
          id        = idAsOpt.get,
          departure = departureAsOpt.get,
          arrival   = arrivalAsOpt.get
        )

        Right(bus)
      }
    } catch {
      case t: Throwable =>
        val errors = Errors(CommonError.invalidData)

        Log.error(t, "Bus.fromJson", s"""Failed to create bus from "$json"!""")

        Left(errors)
    }
  }
}
