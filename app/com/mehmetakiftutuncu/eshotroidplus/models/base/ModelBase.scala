package com.mehmetakiftutuncu.eshotroidplus.models.base

import play.api.libs.json.JsObject

trait ModelBase {
  def toJson: JsObject

  override def toString: String = toJson.toString()
}
