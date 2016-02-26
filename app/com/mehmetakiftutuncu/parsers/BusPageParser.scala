package com.mehmetakiftutuncu.parsers

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models._
import com.mehmetakiftutuncu.utilities.{ConfBase, HttpBase, StringUtils}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

/**
  * Created by akif on 16/02/16.
  */
object BusPageParser extends BusPageParserBase {
  override protected def Conf: ConfBase = com.mehmetakiftutuncu.utilities.Conf
  override protected def Http: HttpBase = com.mehmetakiftutuncu.utilities.Http
}

trait BusPageParserBase {
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

  /* 1st occurrence of "timesUlStart" and "timesUlEnd" is for times for workdays from "Departure" location
   * 2nd occurrence of "timesUlStart" and "timesUlEnd" is for times for workdays from "Arrival" location
   * 3rd occurrence of "timesUlStart" and "timesUlEnd" is for times for saturday from "Departure" location
   * 4th occurrence of "timesUlStart" and "timesUlEnd" is for times for saturday from "Arrival" location
   * 5th occurrence of "timesUlStart" and "timesUlEnd" is for times for sunday from "Departure" location
   * 6th occurrence of "timesUlStart" and "timesUlEnd" is for times for sunday from "Arrival" location
   *
   * Inside each occurrence, every match to "timeLiRegex" gives "hour" and "minute" respectively with group(1) and group(2) */
  private val timesUlStart = """<ul class="timescape">"""
  private val timesUlEnd   = """</ul>"""
  private val timesUlRegex = """<ul.+?>([\s\S]+)<\/ul>""".r
  private val timeLiRegex  = """<span>(\d{2}):(\d{2})<\/span>""".r

  def getAndParseStops(busId: Int, direction: Direction): Future[Either[Errors, List[Stop]]] = {
    getBusPage(busId, direction).map {
      case Left(errors) =>
        Left(errors)

      case Right(eshotBusPageString) =>
        extractStopsUl(eshotBusPageString) match {
          case Left(extractUlErrors) =>
            Left(extractUlErrors)

          case Right(stopsUlString) =>
            stopsUlRegex.findFirstMatchIn(stopsUlString).map(_.group(1)) map {
              stopLisString =>
                val stopLiStringList = stopLisString.split("\\n").collect {
                  case liString if liString.trim.nonEmpty =>
                    liString.trim
                }.toList

                val (parseStopsErrors: Errors, stops: List[Stop]) = stopLiStringList.foldLeft((Errors.empty, List.empty[Stop])) {
                  case ((errors: Errors, stops: List[Stop]), stopLiString: String) =>
                    extractStopFromLi(stopLiString, busId, direction) match {
                      case Left(e)     => (errors ++ e, stops)
                      case Right(stop) => (errors, stops :+ stop)
                    }
                }

                if (parseStopsErrors.nonEmpty) {
                  Left(parseStopsErrors)
                } else {
                  Right(stops)
                }
            } getOrElse {
              Left(Errors(CommonError.invalidData.reason("Could not extract stops!")))
            }
      }
    }
  }

  def getAndParseTimes(busId: Int): Future[Either[Errors, List[Time]]] = {
    getBusPage(busId, Directions.Departure).map {
      case Left(errors) =>
        Left(errors)

      case Right(eshotBusPageString) =>
        val (errors: Errors, timeList: List[Time], _) = (1 to 6).foldLeft((Errors.empty, List.empty[Time], 0)) {
          case ((errors: Errors, timeList: List[Time], startIndexInPage: Int), currentRun: Int) =>
            if (errors.nonEmpty) {
              (errors, timeList, startIndexInPage)
            } else {
              val (dayType: DayType, direction: Direction) = currentRun match {
                case 1 => DayTypes.WeekDays -> Directions.Departure
                case 2 => DayTypes.WeekDays -> Directions.Arrival
                case 3 => DayTypes.Saturday -> Directions.Departure
                case 4 => DayTypes.Saturday -> Directions.Arrival
                case 5 => DayTypes.Sunday -> Directions.Departure
                case 6 => DayTypes.Sunday -> Directions.Arrival
              }

              extractTimesUl(eshotBusPageString, startIndexInPage) match {
                case Left(extractUlErrors) =>
                  (errors ++ extractUlErrors, timeList, startIndexInPage)

                case Right((timesUlString, newStartIndex)) =>
                  timesUlRegex.findFirstMatchIn(timesUlString).map(_.group(1)) map {
                    timeLisString =>
                      val timeLiStringList = timeLisString.split("\\n").collect {
                        case liString if liString.trim.nonEmpty && liString.contains("span") =>
                          liString.trim
                      }.toList

                      val (parseTimesErrors: Errors, times: List[Time]) = timeLiStringList.foldLeft((Errors.empty, List.empty[Time])) {
                        case ((errors: Errors, times: List[Time]), timeLiString: String) =>
                          extractTimeFromLi(timeLiString, busId, dayType, direction) match {
                            case Left(e)     => (errors ++ e, times)
                            case Right(time) => (errors, times :+ time)
                          }
                      }

                      if (parseTimesErrors.nonEmpty) {
                        (errors ++ parseTimesErrors, timeList, newStartIndex)
                      } else {
                        (errors, timeList ++ times, newStartIndex)
                      }
                  } getOrElse {
                    (errors + CommonError.invalidData.reason("Could not extract times!"), timeList, newStartIndex)
                  }
              }
            }
        }

        if (errors.nonEmpty) {
          Left(errors)
        } else {
          Right(timeList)
        }
    }
  }

  private def getBusPage(busId: Int, direction: Direction): Future[Either[Errors, String]] = {
    Http.postFormAsString(Conf.Hosts.busPage, Map(busIdKey -> Seq(busId.toString), directionKey -> Seq(direction.number.toString)))
  }

  private def extractStopsUl(eshotBusPageString: String): Either[Errors, String] = {
    val startIndex = eshotBusPageString.indexOf(stopsUlStart)

    if (startIndex < 0) {
      Left(Errors(CommonError.invalidData.reason("Could not find starting point of bus stops list!")))
    } else {
      val endIndex = eshotBusPageString.indexOf(stopsUlEnd, startIndex)

      if (endIndex < 0) {
        Left(Errors(CommonError.invalidData.reason("Could not find ending point of bus stops list!")))
      } else {
        val stopsUl = eshotBusPageString.substring(startIndex, endIndex + stopsUlEnd.length)

        Right(stopsUl)
      }
    }
  }

  private def extractStopFromLi(stopLiString: String, busId: Int, direction: Direction): Either[Errors, Stop] = {
    stopLiRegex.findFirstMatchIn(stopLiString) map {
      stopMatch =>
        val (stopIdString: String, rawName: String) = (stopMatch.group(1), stopMatch.group(2))

        if (Try(stopIdString.toInt).toOption.exists(_ <= 0)) {
          Left(Errors(CommonError.invalidData.reason("Received invalid stop id!").data(stopIdString)))
        } else {
          val name = StringUtils.sanitizeHtml(rawName)

          val stop = Stop(stopIdString.toInt, name, busId, direction, Location(0.0, 0.0))

          Right(stop)
        }
    } getOrElse {
      Left(Errors(CommonError.invalidData.reason("Received invalid stop li string!").data(stopLiString)))
    }
  }

  private def extractTimesUl(eshotBusPageString: String, startIndexInPage: Int): Either[Errors, (String, Int)] = {
    val startIndex = eshotBusPageString.indexOf(timesUlStart, startIndexInPage)

    if (startIndex < 0) {
      Left(Errors(CommonError.invalidData.reason("Could not find starting point of bus times list!").data(startIndexInPage.toString)))
    } else {
      val endIndex = eshotBusPageString.indexOf(timesUlEnd, startIndex)

      if (endIndex < 0) {
        Left(Errors(CommonError.invalidData.reason("Could not find ending point of bus times list!").data(startIndexInPage.toString)))
      } else {
        val finalEndIndex: Int = endIndex + timesUlEnd.length

        val timesUl = eshotBusPageString.substring(startIndex, finalEndIndex)

        Right(timesUl -> finalEndIndex)
      }
    }
  }

  private def extractTimeFromLi(timeLiString: String, busId: Int, dayType: DayType, direction: Direction): Either[Errors, Time] = {
    timeLiRegex.findFirstMatchIn(timeLiString) map {
      timeMatch =>
        val (hourString: String, minuteString: String) = (timeMatch.group(1), timeMatch.group(2))

        if (Try(hourString.toInt).toOption.exists(h => h < 0 && h > 23)) {
          Left(Errors(CommonError.invalidData.reason("Received invalid hour!").data(hourString)))
        } else if (Try(minuteString.toInt).toOption.exists(h => h < 0 && h > 59)) {
          Left(Errors(CommonError.invalidData.reason("Received invalid minute!").data(minuteString)))
        } else {
          val time = Time(busId, dayType, direction, hourString.toInt, minuteString.toInt)

          Right(time)
        }
    } getOrElse {
      Left(Errors(CommonError.invalidData.reason("Received invalid time li string!").data(timeLiString)))
    }
  }
}
