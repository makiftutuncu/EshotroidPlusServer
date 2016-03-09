package com.mehmetakiftutuncu.eshotroidplus.models

import com.mehmetakiftutuncu.eshotroidplus.utilities.base.EnumBase

sealed trait DayType

object DayTypes extends EnumBase[DayType] {
  case object WeekDays extends DayType
  case object Saturday extends DayType
  case object Sunday   extends DayType

  override val values: Set[DayType] = Set(WeekDays, Saturday, Sunday)
}
