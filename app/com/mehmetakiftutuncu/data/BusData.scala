package com.mehmetakiftutuncu.data

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import com.mehmetakiftutuncu.models._
import com.mehmetakiftutuncu.parsers.{BusListParserBase, BusPageParserBase}
import com.mehmetakiftutuncu.utilities.{CacheBase, Log}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object BusData extends BusDataBase {
  override protected def Bus: BusBase                     = com.mehmetakiftutuncu.models.Bus
  override protected def BusListParser: BusListParserBase = com.mehmetakiftutuncu.parsers.BusListParser
  override protected def BusPageParser: BusPageParserBase = com.mehmetakiftutuncu.parsers.BusPageParser
  override protected def Cache: CacheBase                 = com.mehmetakiftutuncu.utilities.Cache
  override protected def Stop: StopBase                   = com.mehmetakiftutuncu.models.Stop
  override protected def Time: TimeBase                   = com.mehmetakiftutuncu.models.Time
}

trait BusDataBase {
  protected def Bus: BusBase
  protected def BusListParser: BusListParserBase
  protected def BusPageParser: BusPageParserBase
  protected def Cache: CacheBase
  protected def Stop: StopBase
  protected def Time: TimeBase

  val busListCacheKey: String                                 = "busList"
  def completeBusDataCacheKey(id: Int): String                = s"bus.$id.completeData"
  def stopsCacheKey(busId: Int, direction: Direction): String = s"bus.stops.$busId.$direction"
  def timesCacheKey(busId: Int): String                       = s"bus.times.$busId"

  def getBusList: Future[Either[Errors, JsValue]] = {
    Cache.getJson(busListCacheKey) match {
      case Some(busListFromCache) =>
        Log.debug("BusData.getBusList", "Awesome! Found bus list on cache.")

        Future.successful(Right(busListFromCache))

      case None =>
        Log.debug("BusData.getBusList", "Did not find bus list on cache.")

        Bus.getBusListFromDB match {
          case Left(getBusListFromDBErrors) =>
            Log.error("BusData.getBusList", "Failed to get bus list from database!", getBusListFromDBErrors)

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
                  Cache.setJson(busListCacheKey, busListJson)

                  Right(busListJson)
                }
            }
        }
    }
  }

  def getBus(id: Int): Future[Either[Errors, JsValue]] = {
    Cache.getJson(completeBusDataCacheKey(id)) match {
      case Some(busFromCache) =>
        Log.debug("BusData.getBus", s"Awesome! Found complete data for bus $id on cache.")

        Future.successful(Right(busFromCache))

      case None =>
        Log.debug("BusData.getBus", s"Did not find complete data for bus $id on cache.")

        Bus.getBusFromDB(id) match {
          case Left(getBusFromDBErrors) =>
            Log.error("BusData.getBus", s"Failed to get bus $id from database!", getBusFromDBErrors)

            Future.successful(Left(getBusFromDBErrors))

          case Right(None) =>
            Log.error("BusData.getBus", s"Bus $id is not found on database!")

            Future.successful(Left(Errors(CommonError("notFound").reason("Bus is not found!").data(id.toString))))

          case Right(Some(bus)) =>
            Log.debug("BusData.getBus", s"Yay! Bus $id is found on database!")

            val busJson = bus.toJson

            val errorsOrTimes = getTimes(id)

            errorsOrTimes.flatMap {
              case Left(timesErrors) =>
                Future.successful(Left(timesErrors))

              case Right(timesJson) =>
                val finalTimesJson = Json.obj("times" -> timesJson)

                val errorsOrStopsFromDeparture = getStops(id, Directions.Departure)

                errorsOrStopsFromDeparture.flatMap {
                  case Left(stopsFromDepartureErrors) =>
                    Future.successful(Left(stopsFromDepartureErrors))

                  case Right(stopsFromDepartureJson) =>
                    val errorsOrStopsFromArrival = getStops(id, Directions.Arrival)

                    errorsOrStopsFromArrival.map {
                      case Left(stopsFromArrivalErrors) =>
                        Left(stopsFromArrivalErrors)

                      case Right(stopsFromArrivalJson) =>
                        val finalStopsJson = Json.obj("stops" -> (stopsFromDepartureJson ++ stopsFromArrivalJson))

                        val completeBusData = busJson ++ finalTimesJson ++ finalStopsJson

                        Log.debug("BusData.getBus", s"Caching complete data for bus $id")
                        Cache.setJson(completeBusDataCacheKey(id), completeBusData)

                        Right(completeBusData)
                    }
                }
            }
        }
    }
  }

  def getStops(busId: Int, direction: Direction): Future[Either[Errors, JsObject]] = {
    Cache.getJson(stopsCacheKey(busId, direction)) match {
      case Some(stopsFromCache) =>
        Log.debug("BusData.getStops", s"Awesome! Found stops of bus $busId from $direction on cache.")

        Future.successful(Right(stopsFromCache.as[JsObject]))

      case None =>
        Log.debug("BusData.getStops", s"Did not find stops of bus $busId from $direction on cache.")

        Stop.getStopsFromDB(busId, direction) match {
          case Left(getStopsFromDBErrors) =>
            Log.error("BusData.getStops", s"Failed to get stops of bus $busId from $direction from database!", getStopsFromDBErrors)

            Future.successful(Left(getStopsFromDBErrors))

          case Right(stopsFromDB) =>
            val futureErrorsOrStops: Future[Either[Errors, List[Stop]]] = if (stopsFromDB.isEmpty) {
              Log.debug("BusData.getStops", s"Oh oh! No stops of bus $busId from $direction on database. Let's download!")

              BusPageParser.getAndParseStops(busId, direction)
            } else {
              Log.debug("BusData.getStops", s"Cool! Found stops of bus $busId from $direction on database.")

              Future.successful(Right(stopsFromDB))
            }

            futureErrorsOrStops.map {
              case Left(errors) =>
                Left(errors)

              case Right(finalStops) =>
                val saveErrors = if (stopsFromDB.isEmpty) {
                  Log.debug("BusData.getStops", s"Saving stops of bus $busId from $direction to database.")

                  Stop.saveStopsToDB(finalStops)
                } else {
                  Errors.empty
                }

                if (saveErrors.nonEmpty) {
                  Left(saveErrors)
                } else {
                  val stopsJson: JsObject = Json.obj(
                    direction.toString.toLowerCase -> Json.toJson(finalStops.map(_.toJson - "busId" - "direction")).as[JsArray]
                  )

                  Log.debug("BusData.getStops", s"Caching stops of bus $busId from $direction.")
                  Cache.setJson(stopsCacheKey(busId, direction), stopsJson)

                  Right(stopsJson)
                }
            }
        }
    }
  }

  def getTimes(busId: Int): Future[Either[Errors, JsObject]] = {
    Cache.getJson(timesCacheKey(busId)) match {
      case Some(timesFromCache) =>
        Log.debug("BusData.getTimes", s"Awesome! Found times of bus $busId on cache.")

        Future.successful(Right(timesFromCache.as[JsObject]))

      case None =>
        Log.debug("BusData.getTimes", s"Did not find times of bus $busId on cache.")

        Time.getTimesFromDB(busId) match {
          case Left(getTimesFromDBErrors) =>
            Log.error("BusData.getTimes", s"Failed to get times of bus $busId from database!", getTimesFromDBErrors)

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
                val saveErrors = if (timesFromDB.isEmpty) {
                  Log.debug("BusData.getTimes", s"Saving times of bus $busId to database.")

                  Time.saveTimesToDB(finalTimes)
                } else {
                  Errors.empty
                }

                if (saveErrors.nonEmpty) {
                  Left(saveErrors)
                } else {
                  val weekDaysTimes: List[Time] = finalTimes.filter(_.dayType == DayTypes.WeekDays)
                  val saturdayTimes: List[Time] = finalTimes.filter(_.dayType == DayTypes.Saturday)
                  val sundayTimes: List[Time]   = finalTimes.filter(_.dayType == DayTypes.Sunday)

                  val timesJson: JsObject = Json.obj(
                    "weekDays" -> Json.obj(
                      "departure" -> Json.toJson(weekDaysTimes.filter(_.direction == Directions.Departure).map(_.toTimeString)).as[JsArray],
                      "arrival"   -> Json.toJson(weekDaysTimes.filter(_.direction == Directions.Departure).map(_.toTimeString)).as[JsArray]
                    ),
                    "saturday" -> Json.obj(
                      "departure" -> Json.toJson(saturdayTimes.filter(_.direction == Directions.Departure).map(_.toTimeString)).as[JsArray],
                      "arrival"   -> Json.toJson(saturdayTimes.filter(_.direction == Directions.Departure).map(_.toTimeString)).as[JsArray]
                    ),
                    "sunday" -> Json.obj(
                      "departure" -> Json.toJson(sundayTimes.filter(_.direction == Directions.Departure).map(_.toTimeString)).as[JsArray],
                      "arrival"   -> Json.toJson(sundayTimes.filter(_.direction == Directions.Departure).map(_.toTimeString)).as[JsArray]
                    )
                  )

                  Log.debug("BusData.getTimes", s"Caching times of bus $busId.")
                  Cache.setJson(timesCacheKey(busId), timesJson)

                  Right(timesJson)
                }
            }
        }
    }
  }
}
