package com.mehmetakiftutuncu.eshotroidplus.models.base

import com.github.mehmetakiftutuncu.errors.Errors
import play.api.libs.json.{JsObject, JsValue}

trait Jsonable[M <: ModelBase] {
  def toJson(model: M): JsObject
  def fromJson(json: JsValue): Either[Errors, M]
}
