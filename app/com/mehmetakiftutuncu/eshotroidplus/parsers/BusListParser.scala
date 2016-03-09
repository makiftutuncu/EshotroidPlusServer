package com.mehmetakiftutuncu.eshotroidplus.parsers

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.eshotroidplus.models.Bus
import com.mehmetakiftutuncu.eshotroidplus.utilities.{ConfBase, HttpBase, Log, StringUtils}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object BusListParser extends BusListParserBase {
  override protected def Conf: ConfBase = com.mehmetakiftutuncu.eshotroidplus.utilities.Conf
  override protected def Http: HttpBase = com.mehmetakiftutuncu.eshotroidplus.utilities.Http
}

trait BusListParserBase {
  protected def Conf: ConfBase
  protected def Http: HttpBase

  private val busListSelectStart = """<select"""
  private val busListSelectEnd   = """</select>"""
  private val busListSelectRegex = """<select.+?>([\s\S]+)<\/select>""".r
  private val busOptionRegex     = """<option.+?>\s*?(\d{1,3})\s*?:\s*?(.+?)\s*?-\s*?(.+?)\s*?<\/option>""".r

  def getAndParseBusList: Future[Either[Errors, List[Bus]]] = {
    Http.getAsString(Conf.Hosts.eshotHome).map {
      case Left(errors) =>
        Left(errors)

      case Right(eshotHomeString) =>
        extractBusListSelect(eshotHomeString) match {
          case Left(extractSelectErrors) =>
            Log.error("BusListParser.getAndParseBusList", "Failed to extract bus list select from page!", extractSelectErrors)

            Left(extractSelectErrors)

          case Right(busListSelect) =>
            busListSelectRegex.findFirstMatchIn(busListSelect).map(_.group(1)) map {
              busOptionsString =>
                val busOptionStringList = busOptionsString.split("\\n").collect {
                  case optionString if !optionString.contains("<option value=\"\"></option>") =>
                    optionString.trim
                }.toList

                val (parseBusListErrors: Errors, busList: List[Bus]) = busOptionStringList.foldLeft((Errors.empty, List.empty[Bus])) {
                  case ((errors: Errors, busList: List[Bus]), busOptionString: String) =>
                    extractBusFromOption(busOptionString) match {
                      case Left(e)    => (errors ++ e, busList)
                      case Right(bus) => (errors, busList :+ bus)
                    }
                }

                if (parseBusListErrors.nonEmpty) {
                  Log.error("BusListParser.getAndParseBusList", "Failed to parse bus list!", parseBusListErrors)

                  Left(parseBusListErrors)
                } else {
                  Right(busList)
                }
            } getOrElse {
              val errors = Errors(CommonError.invalidData.reason("Could not extract bus list!"))

              Log.error("BusListParser.getAndParseBusList", "Failed to extract contents of bus list select!", errors)

              Left(errors)
            }
      }
    }
  }

  private def extractBusListSelect(eshotHomeString: String): Either[Errors, String] = {
    val startIndex = eshotHomeString.indexOf(busListSelectStart)

    if (startIndex < 0) {
      Left(Errors(CommonError.invalidData.reason("Could not find starting point of bus list!")))
    } else {
      val endIndex = eshotHomeString.indexOf(busListSelectEnd, startIndex)

      if (endIndex < 0) {
        Left(Errors(CommonError.invalidData.reason("Could not find ending point of bus list!")))
      } else {
        val busListSelect = eshotHomeString.substring(startIndex, endIndex + busListSelectEnd.length)

        Right(busListSelect)
      }
    }
  }

  private def extractBusFromOption(busOptionString: String): Either[Errors, Bus] = {
    busOptionRegex.findFirstMatchIn(busOptionString) map {
      busMatch =>
        val (busIdString: String, rawDeparture: String, rawArrival: String) = (busMatch.group(1), busMatch.group(2), busMatch.group(3))

        if (!Try(busIdString.toInt).toOption.exists(_ > 0)) {
          Left(Errors(CommonError.invalidData.reason("Received invalid bus id!").data(busIdString)))
        } else {
          val departure = StringUtils.sanitizeHtml(rawDeparture)
          val arrival   = StringUtils.sanitizeHtml(rawArrival)

          val bus = Bus(busIdString.toInt, departure, arrival)

          Right(bus)
        }
    } getOrElse {
      Left(Errors(CommonError.invalidData.reason("Received invalid bus option string!").data(busOptionString)))
    }
  }
}
