package com.mehmetakiftutuncu.models

import com.mehmetakiftutuncu.utilities.base.EnumBase

sealed trait Direction {
  val number: Int
  val value: Boolean
}

object Directions extends EnumBase[Direction] {
  case object Departure extends Direction {override val number: Int = 1; override val value: Boolean = true}
  case object Arrival   extends Direction {override val number: Int = 2; override val value: Boolean = false}

  override val values: Set[Direction] = Set(Departure, Arrival)

  def withNumber(number: Int): Direction = if (number == 1) Departure else if (number == 2) Arrival else throw new NoSuchElementException(s"""No direction with number "$number" is found!""")

  def withValue(value: Boolean): Direction = if (value) Departure else Arrival
}
