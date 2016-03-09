package com.mehmetakiftutuncu.eshotroidplus.utilities.base

trait EnumBase[E] {
  val values: Set[E]

  protected lazy val valueMap: Map[String, E] = values.map(v => toName(v) -> v).toMap

  def toName(value: E): String = value.toString

  def withName(name: String): E = valueMap.getOrElse(name, throw new NoSuchElementException(s"""No item with name "$name" is found!"""))

  def withNameOptional(name: String): Option[E] = valueMap.get(name)
}
