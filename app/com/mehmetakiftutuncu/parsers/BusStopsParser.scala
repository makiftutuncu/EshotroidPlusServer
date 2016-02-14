package com.mehmetakiftutuncu.parsers

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models.{Location, Stop, Direction}
import com.mehmetakiftutuncu.utilities.{StringUtils, HttpBase, ConfBase}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object BusStopsParser extends BusStopsParserBase {
  override protected def Conf: ConfBase = com.mehmetakiftutuncu.utilities.Conf
  override protected def Http: HttpBase = com.mehmetakiftutuncu.utilities.Http
}

trait BusStopsParserBase {
  protected def Conf: ConfBase
  protected def Http: HttpBase

  /* Keys to the form in request body to get bus details
   *
   * Construct following as the form:
   *
   * Map(busIdKey -> Seq(busId), directionKey -> Seq(direction.number))
   *
   * where direction is one of "Direction"s in "com.mehmetakiftutuncu.models.Directions" */
  private val busIdKey     = "hatId"
  private val directionKey = "hatYon"

  /* Only occurrence of "stopsUlStart" and "stopsUlEnd" is for list of stops
   *
   * Inside the occurrence, every match to "stopLiRegex" gives "stopId" and "stopName" respectively with group(1) and group(2) */
  private val stopsUlStart = """<ul class="transfer">"""
  private val stopsUlEnd   = """</ul>"""
  private val stopsUlRegex = """<ul.+?>([\s\S]+)<\/ul>""".r
  private val stopLiRegex  = """<li.+?id="(\d+)">(.+?)<\/li>""".r

  def getAndParseBusStops(busId: Int, direction: Direction): Future[Either[Errors, List[Stop]]] = {
    Http.postFormAsString(Conf.Hosts.busPage, Map(busIdKey -> Seq(busId.toString), directionKey -> Seq(direction.number.toString))).map {
      case Left(errors) => Left(errors)

      case Right(eshotBusPageString) => extractBusStopsUl(eshotBusPageString) match {
        case Left(extractUlErrors) => Left(extractUlErrors)

        case Right(busStopsUlString) =>
          stopsUlRegex.findFirstMatchIn(busStopsUlString).map(_.group(1)) map {
            busStopLisString =>
              val busStopLiStringList = busStopLisString.split("\\n").collect {
                case liString if liString.trim.nonEmpty =>
                  liString.trim
              }.toList

              val (parseBusStopsErrors: Errors, busStops: List[Stop]) = busStopLiStringList.foldLeft((Errors.empty, List.empty[Stop])) {
                case ((errors: Errors, busStops: List[Stop]), busLiString: String) =>
                  extractStopFromLi(busLiString, busId, direction) match {
                    case Left(e)     => (errors ++ e, busStops)
                    case Right(stop) => (errors, busStops :+ stop)
                  }
              }

              if (parseBusStopsErrors.nonEmpty) {
                Left(parseBusStopsErrors)
              } else {
                Right(busStops)
              }
          } getOrElse {
            Left(Errors(CommonError.invalidData.reason("Could not extract bus stops!")))
          }
      }
    }
  }

  private def extractBusStopsUl(eshotBusPageString: String): Either[Errors, String] = {
    val startIndex = eshotBusPageString.indexOf(stopsUlStart)

    if (startIndex < 0) {
      Left(Errors(CommonError.invalidData.reason("Could not find starting point of bus stops list!")))
    } else {
      val endIndex = eshotBusPageString.indexOf(stopsUlEnd, startIndex)

      if (endIndex < 0) {
        Left(Errors(CommonError.invalidData.reason("Could not find ending point of bus stops list!")))
      } else {
        val busStopsUl = eshotBusPageString.substring(startIndex, endIndex + stopsUlEnd.length)

        Right(busStopsUl)
      }
    }
  }

  private def extractStopFromLi(busStopLiString: String, busId: Int, direction: Direction): Either[Errors, Stop] = {
    stopLiRegex.findFirstMatchIn(busStopLiString) map {
      stopMatch =>
        val (stopIdString: String, rawName: String) = (stopMatch.group(1), stopMatch.group(2))

        if (!Try(stopIdString.toInt).toOption.exists(_ > 0)) {
          Left(Errors(CommonError.invalidData.reason("Received invalid stop id!").data(stopIdString)))
        } else {
          val name = StringUtils.sanitizeHtml(rawName)

          val stop = Stop(stopIdString.toInt, name, busId, direction, Location(0.0, 0.0))

          Right(stop)
        }
    } getOrElse {
      Left(Errors(CommonError.invalidData.reason("Received invalid stop li string!").data(busStopLiString)))
    }
  }
}
