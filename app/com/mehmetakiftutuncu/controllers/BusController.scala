package com.mehmetakiftutuncu.controllers

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.BusListBase
import com.mehmetakiftutuncu.parsers.BusStopsParserBase
import com.mehmetakiftutuncu.utilities.base.ControllerBase
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class BusController extends BusControllerBase {
  override protected def BusList: BusListBase               = com.mehmetakiftutuncu.models.BusList
  override protected def BusStopsParser: BusStopsParserBase = com.mehmetakiftutuncu.parsers.BusStopsParser
}

trait BusControllerBase extends ControllerBase {
  protected def BusList: BusListBase
  protected def BusStopsParser: BusStopsParserBase

  def list = Action.async {
    BusList.getBusList map {
      case Left(errors) => okWithError(errors)

      case Right(busListJson) =>
        okWithJson(busListJson)
    }
  }

  def getBus(busId: Int) = Action.async {
    if (busId <= 0) {
      futureOkWithError(Errors(CommonError.invalidData.reason("Bus id must be > 0!").data(busId.toString)))
    } else {
      futureOkWithJson(Json.obj())
    }
  }
}
