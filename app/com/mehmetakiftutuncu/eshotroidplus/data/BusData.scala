package com.mehmetakiftutuncu.eshotroidplus.data

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.eshotroidplus.models._
import com.mehmetakiftutuncu.eshotroidplus.parsers.{BusListParserBase, BusPageParserBase}
import com.mehmetakiftutuncu.eshotroidplus.utilities.{ConfBase, Log}
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object BusData extends BusDataBase {
  override protected def Bus: BusBase                     = com.mehmetakiftutuncu.eshotroidplus.models.Bus
  override protected def BusListParser: BusListParserBase = com.mehmetakiftutuncu.eshotroidplus.parsers.BusListParser
  override protected def BusPageParser: BusPageParserBase = com.mehmetakiftutuncu.eshotroidplus.parsers.BusPageParser
  override protected def Conf: ConfBase                   = com.mehmetakiftutuncu.eshotroidplus.utilities.Conf
  override protected def RoutePoint: RoutePointBase       = com.mehmetakiftutuncu.eshotroidplus.models.RoutePoint
  override protected def Stop: StopBase                   = com.mehmetakiftutuncu.eshotroidplus.models.Stop
  override protected def Time: TimeBase                   = com.mehmetakiftutuncu.eshotroidplus.models.Time
}

trait BusDataBase {
  protected def Bus: BusBase
  protected def BusListParser: BusListParserBase
  protected def BusPageParser: BusPageParserBase
  protected def Conf: ConfBase
  protected def RoutePoint: RoutePointBase
  protected def Stop: StopBase
  protected def Time: TimeBase

  val busListCacheKey: String                   = "busList"
  def everythingForBusCacheKey(id: Int): String = s"bus.everything.$id"
  def busCacheKey(id: Int): String              = s"bus.$id"
  def timesCacheKey(busId: Int): String         = s"bus.times.$busId"
  def stopsCacheKey(busId: Int): String         = s"bus.stops.$busId"
  def routePointsCacheKey(busId: Int): String   = s"bus.routePoints.$busId"

  def getBusListJson: Future[Either[Errors, JsValue]] = {
    Cache.getAs[JsValue](busListCacheKey) match {
      case Some(busListFromCache) =>
        Log.debug("BusData.getBusList", "Awesome! Found bus list on cache.")

        Future.successful(Right(busListFromCache))

      case None =>
        Log.debug("BusData.getBusList", "Did not find bus list on cache.")

        Bus.getBusListFromDB match {
          case Left(getBusListFromDBErrors) =>
            Future.successful(Left(getBusListFromDBErrors))

          case Right(busListFromDB) =>
            val futureErrorsOrBusList: Future[Either[Errors, List[Bus]]] = if (busListFromDB.isEmpty) {
              Log.debug("BusData.getBusList", "Oh oh! No bus list on database. Let's download!")

              BusListParser.getAndParseBusList
            } else {
              Log.debug("BusData.getBusList", "Cool! Found bus list on database.")

              Future.successful(Right(busListFromDB))
            }

            futureErrorsOrBusList.map {
              case Left(errors) =>
                Left(errors)

              case Right(finalBusList) =>
                val saveErrors = if (busListFromDB.isEmpty) {
                  Log.debug("BusData.getBusList", "Saving bus list to database.")

                  Bus.saveBusListToDB(finalBusList)
                } else {
                  Errors.empty
                }

                if (saveErrors.nonEmpty) {
                  Left(saveErrors)
                } else {
                  val busListJson: JsArray = Json.toJson(finalBusList.map(_.toJson)).as[JsArray]

                  Log.debug("BusData.getBusList", "Caching bus list.")
                  Cache.set(busListCacheKey, busListJson)

                  Right(busListJson)
                }
            }
        }
    }
  }

  def getBusJson(id: Int): Future[Either[Errors, JsValue]] = {
    Cache.getAs[JsValue](everythingForBusCacheKey(id)) match {
      case Some(busFromCache: JsValue) =>
        Log.debug("BusData.getEverythingForBus", s"Awesome! Found all data for bus $id on cache.")

        Future.successful(Right(busFromCache))

      case None =>
        Log.debug("BusData.getEverythingForBus", s"Did not find all data for bus $id on cache.")

        getBus(id) match {
          case Left(getBusErrors: Errors) =>
            Future.successful(Left(getBusErrors))

          case Right(bus: Bus) =>
            val errorsOrTimes: Future[Either[Errors, List[Time]]] = getTimes(id)

            errorsOrTimes.flatMap {
              case Left(timesErrors: Errors) =>
                Future.successful(Left(timesErrors))

              case Right(times: List[Time]) =>
                val errorsOrStops: Future[Either[Errors, List[Stop]]] = getStops(id)

                errorsOrStops.flatMap {
                  case Left(getStopsErrors: Errors) =>
                    Future.successful(Left(getStopsErrors))

                  case Right(stops: List[Stop]) =>
                    val errorsOrRoutePoints: Future[Either[Errors, List[RoutePoint]]] = getRoutePoints(id)

                    errorsOrRoutePoints.map {
                      case Left(getRoutePointsErrors: Errors) =>
                        Left(getRoutePointsErrors)

                      case Right(routePoints: List[RoutePoint]) =>
                        // Get actual locations of stops
                        val finalStops = stops.map(s => routePoints.find(r => r.description == s.name && r.direction == s.direction).map(r => s.copy(location = r.location)).getOrElse(s))

                        val saveTimesErrors = {
                          Log.debug("BusData.getEverythingForBus", s"Saving times of bus $id to database.")

                          Time.saveTimesToDB(times)
                        }

                        val saveStopsErrors = if (saveTimesErrors.isEmpty) {
                          Log.debug("BusData.getEverythingForBus", s"Saving stops of bus $id to database.")

                          Stop.saveStopsToDB(finalStops)
                        } else {
                          saveTimesErrors
                        }

                        val saveRoutePointsErrors = if (saveStopsErrors.isEmpty) {
                          Log.debug("BusData.getEverythingForBus", s"Saving route points of bus $id to database.")

                          RoutePoint.saveRoutePointsToDB(routePoints)
                        } else {
                          saveStopsErrors
                        }

                        if (saveRoutePointsErrors.nonEmpty) {
                          Left(saveRoutePointsErrors)
                        } else {
                          val busJson: JsObject = bus.toJson

                          val weekDaysTimes: List[Time] = times.filter(_.dayType == DayTypes.WeekDays)
                          val saturdayTimes: List[Time] = times.filter(_.dayType == DayTypes.Saturday)
                          val sundayTimes: List[Time]   = times.filter(_.dayType == DayTypes.Sunday)

                          val timesJson: JsObject = Json.obj(
                            "times" -> Json.obj(
                              "weekDays" -> Json.obj(
                                "departure" -> Json.toJson(weekDaysTimes.filter(_.direction == Directions.Departure).map(_.time)).as[JsArray],
                                "arrival"   -> Json.toJson(weekDaysTimes.filter(_.direction == Directions.Arrival).map(_.time)).as[JsArray]
                              ),
                              "saturday" -> Json.obj(
                                "departure" -> Json.toJson(saturdayTimes.filter(_.direction == Directions.Departure).map(_.time)).as[JsArray],
                                "arrival"   -> Json.toJson(saturdayTimes.filter(_.direction == Directions.Arrival).map(_.time)).as[JsArray]
                              ),
                              "sunday" -> Json.obj(
                                "departure" -> Json.toJson(sundayTimes.filter(_.direction == Directions.Departure).map(_.time)).as[JsArray],
                                "arrival"   -> Json.toJson(sundayTimes.filter(_.direction == Directions.Arrival).map(_.time)).as[JsArray]
                              )
                            )
                          )

                          val stopsFromDeparture = finalStops.filter(_.direction == Directions.Departure)
                          val stopsFromArrival   = finalStops.filter(_.direction == Directions.Arrival)

                          val stopsJson: JsObject = Json.obj(
                            "stops" -> Json.obj(
                              "departure" -> Json.toJson(stopsFromDeparture.map(_.toJson - "busId" - "direction")).as[JsArray],
                              "arrival"   -> Json.toJson(stopsFromArrival.map(_.toJson - "busId" - "direction")).as[JsArray]
                            )
                          )

                          val routePointsFromDeparture = routePoints.filter(_.direction == Directions.Departure)
                          val routePointsFromArrival   = routePoints.filter(_.direction == Directions.Arrival)

                          val routePointsJson: JsObject = Json.obj(
                            "route" -> Json.obj(
                              "departure" -> Json.toJson(routePointsFromDeparture.map(_.location.toJson)).as[JsArray],
                              "arrival"   -> Json.toJson(routePointsFromArrival.map(_.location.toJson)).as[JsArray]
                            )
                          )

                          val completeBusData: JsObject = busJson ++ timesJson ++ stopsJson ++ routePointsJson

                          Log.debug("BusData.getEverythingForBus", s"Caching complete data for bus $id")
                          Cache.set(everythingForBusCacheKey(id), completeBusData)

                          Right(completeBusData)
                        }
                    }
                }
            }
        }
    }
  }

  private def getBus(id: Int): Either[Errors, Bus] = {
    Cache.getAs[Bus](busCacheKey(id)) match {
      case Some(busFromCache: Bus) =>
        Log.debug("BusData.getBus", s"Awesome! Found bus $id on cache.")

        Right(busFromCache)

      case None =>
        Log.debug("BusData.getBus", s"Did not find bus $id on cache.")

        Bus.getBusFromDB(id) match {
          case Left(getBusFromDBErrors: Errors) =>
            Left(getBusFromDBErrors)

          case Right(None) =>
            Log.error("BusData.getBus", s"Bus $id is not found on database!")

            Left(Errors(CommonError("notFound").reason("Bus is not found!").data(id.toString)))

          case Right(Some(bus: Bus)) =>
            Log.debug("BusData.getBus", s"Yay! Bus $id is found on database.")

            Cache.set(busCacheKey(id), bus, Conf.Cache.cacheTTLInSeconds)

            Right(bus)
        }
    }
  }

  private def getTimes(busId: Int): Future[Either[Errors, List[Time]]] = {
    Cache.getAs[List[Time]](timesCacheKey(busId)) match {
      case Some(timesFromCache) =>
        Log.debug("BusData.getTimes", s"Awesome! Found times of bus $busId on cache.")

        Future.successful(Right(timesFromCache))

      case None =>
        Log.debug("BusData.getTimes", s"Did not find times of bus $busId on cache.")

        Time.getTimesFromDB(busId) match {
          case Left(getTimesFromDBErrors) =>
            Future.successful(Left(getTimesFromDBErrors))

          case Right(timesFromDB) =>
            val futureErrorsOrTimes: Future[Either[Errors, List[Time]]] = if (timesFromDB.isEmpty) {
              Log.debug("BusData.getTimes", s"Oh oh! No times of bus $busId on database. Let's download!")

              BusPageParser.getAndParseTimes(busId)
            } else {
              Log.debug("BusData.getTimes", s"Cool! Found times of bus $busId on database.")

              Future.successful(Right(timesFromDB))
            }

            futureErrorsOrTimes.map {
              case Left(errors) =>
                Left(errors)

              case Right(finalTimes) =>
                Cache.set(timesCacheKey(busId), finalTimes, Conf.Cache.cacheTTLInSeconds)

                Right(finalTimes)
            }
        }
    }
  }

  private def getStops(busId: Int): Future[Either[Errors, List[Stop]]] = {
    Cache.getAs[List[Stop]](stopsCacheKey(busId)) match {
      case Some(stopsFromCache) =>
        Log.debug("BusData.getStops", s"Awesome! Found stops of bus $busId on cache.")

        Future.successful(Right(stopsFromCache))

      case None =>
        Log.debug("BusData.getStops", s"Did not find stops of bus $busId on cache.")

        Stop.getStopsFromDB(busId) match {
          case Left(getStopsFromDBErrors) =>
            Future.successful(Left(getStopsFromDBErrors))

          case Right(stopsFromDB) =>
            if (stopsFromDB.isEmpty) {
              Log.debug("BusData.getStops", s"Oh oh! No stops of bus $busId on database. Let's download!")

              BusPageParser.getAndParseStops(busId, Directions.Departure).flatMap {
                case Left(getStopsFromDepartureErrors) =>
                  Future.successful(Left(getStopsFromDepartureErrors))

                case Right(stopsFromDeparture) =>
                  BusPageParser.getAndParseStops(busId, Directions.Arrival) map {
                    case Left(getStopsFromArrivalErrors) =>
                      Left(getStopsFromArrivalErrors)

                    case Right(stopsFromArrival) =>
                      val stops = stopsFromDeparture ++ stopsFromArrival

                      Cache.set(stopsCacheKey(busId), stops, Conf.Cache.cacheTTLInSeconds)

                      Right(stops)
                  }
              }
            } else {
              Log.debug("BusData.getStops", s"Cool! Found stops of bus $busId on database.")

              Cache.set(stopsCacheKey(busId), stopsFromDB, Conf.Cache.cacheTTLInSeconds)

              Future.successful(Right(stopsFromDB))
            }
        }
    }
  }

  private def getRoutePoints(busId: Int): Future[Either[Errors, List[RoutePoint]]] = {
    Cache.getAs[List[RoutePoint]](routePointsCacheKey(busId)) match {
      case Some(routePointsFromCache) =>
        Log.debug("BusData.getRoutePoints", s"Awesome! Found route points of bus $busId on cache.")

        Future.successful(Right(routePointsFromCache))

      case None =>
        Log.debug("BusData.getRoutePoints", s"Did not find route points of bus $busId on cache.")

        RoutePoint.getRoutePointsFromDB(busId) match {
          case Left(getRoutePointsFromDBErrors) =>
            Future.successful(Left(getRoutePointsFromDBErrors))

          case Right(routePointsFromDB) =>
            if (routePointsFromDB.isEmpty) {
              Log.debug("BusData.getRoutePoints", s"Oh oh! No route points of bus $busId on database. Let's download!")

              BusPageParser.getAndParseRoutePoints(busId, Directions.Departure) flatMap {
                case Left(getRoutePointsFromDepartureErrors) =>
                  Future.successful(Left(getRoutePointsFromDepartureErrors))

                case Right(routePointsFromDeparture) =>
                  BusPageParser.getAndParseRoutePoints(busId, Directions.Arrival) map {
                    case Left(getRoutePointsFromArrivalErrors) =>
                      Left(getRoutePointsFromArrivalErrors)

                    case Right(routePointsFromArrival) =>
                      val routePoints = routePointsFromDeparture ++ routePointsFromArrival

                      Cache.set(routePointsCacheKey(busId), routePoints, Conf.Cache.cacheTTLInSeconds)

                      Right(routePoints)
                  }
              }
            } else {
              Log.debug("BusData.getRoutePoints", s"Cool! Found route points of bus $busId on database.")

              Cache.set(routePointsCacheKey(busId), routePointsFromDB, Conf.Cache.cacheTTLInSeconds)

              Future.successful(Right(routePointsFromDB))
            }
        }
    }
  }
}
