package com.mehmetakiftutuncu.controllers

import com.mehmetakiftutuncu.parsers.BusListParserBase
import com.mehmetakiftutuncu.utilities.base.ControllerBase
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class BusController extends BusControllerBase {
  override protected def BusListParser: BusListParserBase = com.mehmetakiftutuncu.parsers.BusListParser
}

trait BusControllerBase extends ControllerBase {
  protected def BusListParser: BusListParserBase

  def list = Action.async {
    BusListParser.getAndParseBusList map {
      case Left(errors) => okWithError(errors)

      case Right(busList) =>
        val data = Json.toJson(busList.map(_.toJson))

        okWithJson(data)
    }
  }
}
