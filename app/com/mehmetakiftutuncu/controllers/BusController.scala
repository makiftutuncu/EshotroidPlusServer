package com.mehmetakiftutuncu.controllers

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.data.BusDataBase
import com.mehmetakiftutuncu.utilities.base.ControllerBase
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class BusController extends BusControllerBase {
  override protected def BusData: BusDataBase = com.mehmetakiftutuncu.data.BusData
}

trait BusControllerBase extends ControllerBase {
  protected def BusData: BusDataBase

  def list = Action.async {
    BusData.getBusListJson map {
      case Left(errors)       => okWithError(errors)
      case Right(busListJson) => okWithJson(busListJson)
    }
  }

  def getBus(busId: Int) = Action.async {
    if (busId <= 0) {
      futureOkWithError(Errors(CommonError.invalidData.reason("Bus id must be > 0!").data(busId.toString)))
    } else {
      BusData.getBusJson(busId) map {
        case Left(errors)   => okWithError(errors)
        case Right(busJson) => okWithJson(busJson)
      }
    }
  }
}
