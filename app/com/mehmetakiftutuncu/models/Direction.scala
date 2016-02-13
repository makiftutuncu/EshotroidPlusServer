package com.mehmetakiftutuncu.models

import com.mehmetakiftutuncu.utilities.base.EnumBase

sealed trait Direction

object Directions extends EnumBase[Direction] {
  case object Departure extends Direction
  case object Arrival   extends Direction

  override val values: Set[Direction] = Set(Departure, Arrival)
}
