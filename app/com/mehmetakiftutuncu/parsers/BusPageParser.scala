package com.mehmetakiftutuncu.parsers

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models._
import com.mehmetakiftutuncu.utilities.{ConfBase, HttpBase, Log, StringUtils}

import scala.annotation.tailrec
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
   * Inside each occurrence, every match to "timeLiRegex" gives "hour" and "minute" respectively with group(1) and group(2)
   *
   * Should an occurrence of "timesUlStart" couldn't be found, check if "noTimesInfoTextPart" exists close to the last known
   * start point because some of the buses don't work on some days (for example; 736 İYTE bus on sundays) and instead of
   * time list, there is a text info. If these criteria are met, don't error out. Instead return an empty list of times. */
  private val timesUlStart        = """<ul class="timescape">"""
  private val timesUlEnd          = """</ul>"""
  private val timesUlRegex        = """<ul.+?>([\s\S]+)<\/ul>""".r
  private val timeLiRegex         = """<span.*?>(\d{2}):(\d{2})<\/span>""".r
  private val noTimesInfoTextPart = "&#231;alışmamaktadır"

  /* Basically this is a <script> ... </script> with Javascript objects named "pin" in it.
   * Each pin is a point on the map which when drawn together, constructs a bus' route.
   *
   * Each occurrence of "routePointPinRegex" is a pin object. Using "routePointDataRegex" on each occurrence captures
   * "latitude", "longitude" and "description" of that point respectively with group(1), group(2) and group(3).
   *
   * First two are clear. Description however is used to map these points to bus stops. If a description is non-empty and
   * matches "name" of a bus stop, that point is the location of that bus stop.
   */
  private val routePointsScriptStart      = """<script>"""
  private val routePointsDataStart        = """var markers = [];"""
  private val routePointsScriptEnd        = """</script>"""
  private val routePointsScriptRegex      = """<script>([\s\S]+)<\/script>""".r
  private val routePointPinRegex          = """var pin = \{[\s\S]+?\}""".r
  private val routePointDataRegex         = """\{[\s\S]+?title: '',[\s\S]+?lat: '([0-9\.]+)'[\s\S]+?lng: '([0-9\.]+)'[\s\S]+?description: '(.*?)'[\s\S]+?\}""".r
  private val routePointDescriptionRegex  = """<b>(.+)<\/b>""".r

  def getAndParseStops(busId: Int, direction: Direction): Future[Either[Errors, List[Stop]]] = {
    getBusPage(busId, direction).map {
      case Left(errors) =>
        Left(errors)

      case Right(eshotBusPageString) =>
        extractStopsUl(eshotBusPageString) match {
          case Left(extractUlErrors) =>
            Log.error("BusPageParser.getAndParseStops", s"Failed to extract stops ul from page for bus $busId from $direction!", extractUlErrors)

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
                  Log.error("BusPageParser.getAndParseStops", s"Failed to parse stops for bus $busId from $direction!", parseStopsErrors)

                  Left(parseStopsErrors)
                } else {
                  Right(stops)
                }
            } getOrElse {
              val errors = Errors(CommonError.invalidData.reason("Could not extract stops!"))

              Log.error("BusPageParser.getAndParseStops", s"Failed to extract contents of stops ul for bus $busId from $direction!", errors)

              Left(errors)
            }
      }
    }
  }

  def getAndParseTimes(busId: Int): Future[Either[Errors, List[Time]]] = {
    getBusPage(busId, Directions.Departure).map {
      case Left(errors) =>
        Left(errors)

      case Right(eshotBusPageString) =>
        val (errors: Errors, timeSet: Set[Time], _) = (1 to 6).foldLeft((Errors.empty, Set.empty[Time], 0)) {
          case ((errors: Errors, timeSet: Set[Time], startIndexInPage: Int), currentRun: Int) =>
            if (errors.nonEmpty) {
              (errors, timeSet, startIndexInPage)
            } else {
              val (dayType: DayType, direction: Direction) = currentRun match {
                case 1 => DayTypes.WeekDays -> Directions.Departure
                case 2 => DayTypes.WeekDays -> Directions.Arrival
                case 3 => DayTypes.Saturday -> Directions.Departure
                case 4 => DayTypes.Saturday -> Directions.Arrival
                case 5 => DayTypes.Sunday   -> Directions.Departure
                case 6 => DayTypes.Sunday   -> Directions.Arrival
              }

              extractTimesUl(eshotBusPageString, startIndexInPage) match {
                case Left(extractUlErrors) =>
                  // Special and very edge case for some buses that do not work on some days
                  val doesBusNotWorkOnThisDay: Boolean = extractUlErrors.exists {
                    // Thanks to Errors, I can check what the error was and determine if it is recoverable
                    case CommonError("invalidData", reason, lastStartIndex) if reason.contains("starting point") =>
                      eshotBusPageString.indexOf(noTimesInfoTextPart, Try(lastStartIndex.toInt).getOrElse(0)) > 0
                  }

                  if (doesBusNotWorkOnThisDay) {
                    Log.warn("BusPageParser.getAndParseTimes", s"Found no times for bus $busId for $dayType from $direction!")

                    (errors, timeSet, startIndexInPage)
                  } else {
                    Log.error("BusPageParser.getAndParseTimes", s"Failed to extract times ul from page for bus $busId for $dayType from $direction!", extractUlErrors)

                    (errors ++ extractUlErrors, timeSet, startIndexInPage)
                  }

                case Right((timesUlString, newStartIndex)) =>
                  timesUlRegex.findFirstMatchIn(timesUlString).map(_.group(1)) map {
                    timeLisString =>
                      val timeLiStringList = timeLisString.split("\\n").collect {
                        case liString if liString.trim.nonEmpty && liString.contains("span") =>
                          liString.trim
                      }.toList

                      val (parseTimesErrors: Errors, times: Set[Time]) = timeLiStringList.foldLeft((Errors.empty, Set.empty[Time])) {
                        case ((errors: Errors, times: Set[Time]), timeLiString: String) =>
                          extractTimeFromLi(timeLiString, busId, dayType, direction) match {
                            case Left(e)     => (errors ++ e, times)
                            case Right(time) => (errors, times + time)
                          }
                      }

                      if (parseTimesErrors.nonEmpty) {
                        Log.error("BusPageParser.getAndParseTimes", s"Failed to parse times for bus $busId for $dayType from $direction!", parseTimesErrors)

                        (errors ++ parseTimesErrors, timeSet, newStartIndex)
                      } else {
                        (errors, timeSet ++ times, newStartIndex)
                      }
                  } getOrElse {
                    val extractErrors: Errors = Errors(CommonError.invalidData.reason("Could not extract times!"))

                    Log.error("BusPageParser.getAndParseTimes", s"Failed to extract contents of times ul for bus $busId for $dayType from $direction!", extractErrors)

                    (errors ++ extractErrors, timeSet, newStartIndex)
                  }
              }
            }
        }

        if (errors.nonEmpty) {
          Left(errors)
        } else {
          Right(timeSet.toList.sortBy(t => (t.dayType.toString, t.direction.toString, t.time)))
        }
    }
  }

  def getAndParseRoutePoints(busId: Int, direction: Direction): Future[Either[Errors, List[RoutePoint]]] = {
    getBusPage(busId, direction).map {
      case Left(errors) =>
        Left(errors)

      case Right(eshotBusPageString) =>
        extractRoutePointsScript(eshotBusPageString) match {
          case Left(extractRoutePointsScriptErrors) =>
            Log.error("BusPageParser.getAndParseRoutePoints", s"Failed to extract route points script from page for bus $busId from $direction!", extractRoutePointsScriptErrors)

            Left(extractRoutePointsScriptErrors)

          case Right(routePointsScriptString) =>
            routePointsScriptRegex.findFirstMatchIn(routePointsScriptString).map(_.group(1)) map {
              routePointsPinStrings =>
                val (parseRoutePointsErrors: Errors, routePoints: Set[RoutePoint]) = routePointPinRegex.findAllIn(routePointsPinStrings).foldLeft((Errors.empty, Set.empty[RoutePoint])) {
                  case ((routePointsErrors: Errors, routePoints: Set[RoutePoint]), routePointPinString: String) =>
                    extractRoutePointFromPin(routePointPinString, busId, direction) match {
                      case Left(e) =>
                        (routePointsErrors ++ e, routePoints)

                      case Right(routePoint) if routePoints.exists(r => r.location == routePoint.location && r.description.nonEmpty) =>
                        (routePointsErrors, routePoints)

                      case Right(routePoint) =>
                        (routePointsErrors, routePoints + routePoint)
                    }
                }

                if (parseRoutePointsErrors.nonEmpty) {
                  Log.error("BusPageParser.getAndParseRoutePoints", s"Failed to parse route points script for bus $busId from $direction!", parseRoutePointsErrors)

                  Left(parseRoutePointsErrors)
                } else {
                  Right(routePoints.toList.sortBy(p => (p.direction.toString, p.location.latitude, p.location.longitude)))
                }
            } getOrElse {
              val errors = Errors(CommonError.invalidData.reason("Could not extract route points!"))

              Log.error("BusPageParser.getAndParseRoutePoints", s"Failed to extract contents of route points script for bus $busId from $direction!", errors)

              Left(errors)
            }
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
          val time = Time(busId, dayType, direction, f"${hourString.toInt}%02d:${minuteString.toInt}%02d")

          Right(time)
        }
    } getOrElse {
      Left(Errors(CommonError.invalidData.reason("Received invalid time li string!").data(timeLiString)))
    }
  }

  @tailrec private def extractRoutePointsScript(eshotBusPageString: String): Either[Errors, String] = {
    val startIndex = eshotBusPageString.indexOf(routePointsScriptStart)

    if (startIndex < 0) {
      Left(Errors(CommonError.invalidData.reason("Could not find starting point of route points!")))
    } else {
      val dataStartIndex = eshotBusPageString.indexOf(routePointsDataStart, startIndex)
      val endIndex       = eshotBusPageString.indexOf(routePointsScriptEnd, startIndex)

      if (dataStartIndex > 0 && endIndex > 0 && endIndex > dataStartIndex) {
        val routePointsScript = eshotBusPageString.substring(startIndex, endIndex + routePointsScriptEnd.length)

        Right(routePointsScript)
      } else if (dataStartIndex < 0) {
        Left(Errors(CommonError.invalidData.reason("Could not find starting point of route points!")))
      } else if (endIndex < 0) {
        Left(Errors(CommonError.invalidData.reason("Could not find ending point of route points!")))
      } else {
        // We found a <script> tag but it is not the one we wanted (next phrase in it was not "routePointsDataStart")
        extractRoutePointsScript(eshotBusPageString.substring(startIndex))
      }
    }
  }

  private def extractRoutePointFromPin(routePointPinString: String, busId: Int, direction: Direction): Either[Errors, RoutePoint] = {
    routePointDataRegex.findFirstMatchIn(routePointPinString) map {
      routeMatch =>
        val (latitudeString: String, longitudeString: String, rawDescription: String) = (routeMatch.group(1), routeMatch.group(2), routeMatch.group(3))

        if (Try(latitudeString.toDouble).isFailure) {
          Left(Errors(CommonError.invalidData.reason("Received invalid latitude!").data(latitudeString)))
        } else if (Try(longitudeString.toDouble).isFailure) {
          Left(Errors(CommonError.invalidData.reason("Received invalid longitude!").data(longitudeString)))
        } else {
          val latitude: Double  = latitudeString.toDouble
          val longitude: Double = longitudeString.toDouble

          val sanitizedDescription = StringUtils.sanitizeHtml(rawDescription, capitalizeEachWord = false)

          val description = routePointDescriptionRegex.findFirstMatchIn(sanitizedDescription).map(_.group(1)).getOrElse("")

          Right(RoutePoint(busId, direction, description, Location(latitude, longitude)))
        }
    } getOrElse {
      Left(Errors(CommonError.invalidData.reason("Received invalid route point pin string!").data(routePointPinString)))
    }
  }
}
