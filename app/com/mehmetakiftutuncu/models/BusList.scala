package com.mehmetakiftutuncu.models

import com.github.mehmetakiftutuncu.errors.Errors
import com.mehmetakiftutuncu.parsers.BusListParserBase
import com.mehmetakiftutuncu.utilities.{CacheBase, Log}
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object BusList extends BusListBase {
  override protected def Bus: BusBase                     = com.mehmetakiftutuncu.models.Bus
  override protected def BusListParser: BusListParserBase = com.mehmetakiftutuncu.parsers.BusListParser
  override protected def Cache: CacheBase                 = com.mehmetakiftutuncu.utilities.Cache
}

trait BusListBase {
  protected def Bus: BusBase
  protected def BusListParser: BusListParserBase
  protected def Cache: CacheBase

  val busListCacheKey: String = "busList"

  def getBusList: Future[Either[Errors, JsValue]] = {
    Cache.getJson(busListCacheKey) match {
      case Some(busListFromCache) =>
        Log.debug("BusList.getBusList", "Awesome! Found bus list on cache.")

        Future.successful(Right(busListFromCache))

      case None =>
        Log.debug("BusList.getBusList", "Did not find bus list on cache.")

        Bus.getBusListFromDB match {
          case Left(getBusListFromDBErrors) =>
            Log.error("BusList.getBusList", "Failed to get bus list from database!", getBusListFromDBErrors)

            Future.successful(Left(getBusListFromDBErrors))

          case Right(busListFromDB) =>
            val futureErrorsOrBusList: Future[Either[Errors, List[Bus]]] = if (busListFromDB.isEmpty) {
              Log.debug("BusList.getBusList", "Oh oh! No bus list on database. Let's download!")

              BusListParser.getAndParseBusList
            } else {
              Log.debug("BusList.getBusList", "Cool! Found bus list on database.")

              Future.successful(Right(busListFromDB))
            }

            futureErrorsOrBusList.map {
              case Left(errors) => Left(errors)

              case Right(finalBusList) =>
                val saveErrors = if (busListFromDB.isEmpty) {
                  Log.debug("BusList.getBusList", "Saving bus list to database.")

                  Bus.saveBusListToDB(finalBusList)
                } else {
                  Errors.empty
                }

                if (saveErrors.nonEmpty) {
                  Left(saveErrors)
                } else {
                  val busListJson: JsArray = Json.toJson(finalBusList.map(_.toJson)).as[JsArray]

                  Log.debug("BusList.getBusList", "Caching bus list.")
                  Cache.setJson(busListCacheKey, busListJson)

                  Right(busListJson)
                }
            }
        }
    }
  }
}
