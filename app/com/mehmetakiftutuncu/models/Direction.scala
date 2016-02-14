package com.mehmetakiftutuncu.models

import com.mehmetakiftutuncu.utilities.base.EnumBase

sealed trait Direction {val number: Int}

object Directions extends EnumBase[Direction] {
  case object Departure extends Direction {override val number: Int = 0}
  case object Arrival   extends Direction {override val number: Int = 1}

  override val values: Set[Direction] = Set(Departure, Arrival)

  def withNumber(number: Int): Direction = if (number == 0) Departure else if (number == 1) Arrival else throw new NoSuchElementException(s"""No direction with number "$number" is found!""")
}
