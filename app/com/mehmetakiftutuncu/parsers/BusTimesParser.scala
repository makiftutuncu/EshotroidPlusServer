package com.mehmetakiftutuncu.parsers

import com.mehmetakiftutuncu.utilities.{HttpBase, ConfBase}

object BusTimesParser extends BusTimesParserBase {
  override protected def Conf: ConfBase = com.mehmetakiftutuncu.utilities.Conf
  override protected def Http: HttpBase = com.mehmetakiftutuncu.utilities.Http
}

trait BusTimesParserBase {
  protected def Conf: ConfBase
  protected def Http: HttpBase

  /* Keys to the form in request body to get bus details
   *
   * Construct following as the form:
   *
   * Map(busIdKey -> bus.id, directionKey -> direction.number)
   *
   * where direction is one of "Direction"s in "com.mehmetakiftutuncu.models.Directions" */
  private val busIdKey     = "hatId"
  private val directionKey = "hatYon"

  /* 1st occurrence of "timeUlStart" and "timeUlEnd" is for times for workdays from "Departure" location
   * 2nd occurrence of "timeUlStart" and "timeUlEnd" is for times for workdays from "Arrival" location
   * 3rd occurrence of "timeUlStart" and "timeUlEnd" is for times for saturday from "Departure" location
   * 4th occurrence of "timeUlStart" and "timeUlEnd" is for times for saturday from "Arrival" location
   * 5th occurrence of "timeUlStart" and "timeUlEnd" is for times for sunday from "Departure" location
   * 6th occurrence of "timeUlStart" and "timeUlEnd" is for times for sunday from "Arrival" location
   *
   * Inside each occurrence, every match to "timeLiRegex" gives "hour" and "minute" respectively with group(1) and group(2) */
  private val timeUlStart = """<ul class="timescape">"""
  private val timeUlEnd   = """</ul>"""
  private val timeLiRegex = """<li>\s*?<span>(\d{2}):(\d{2})<\/span>\s*?<\/li>""".r
}
