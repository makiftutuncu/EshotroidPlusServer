package com.mehmetakiftutuncu.utilities

import com.github.mehmetakiftutuncu.errors.{CommonError, SimpleError}
import com.github.mehmetakiftutuncu.errors.base.ErrorBase
import com.github.mehmetakiftutuncu.errors.representation.ErrorRepresenter
import play.api.libs.json.{JsNumber, Json, JsValue}

/**
  * This representer represents errors using Play Framework's Json APIs.
  */
object JsonErrorRepresenter extends ErrorRepresenter[JsValue] {
  override def represent(error: ErrorBase): JsValue = {
    val json = error match {
      case SimpleError(name)               => Json.obj("name" -> name)
      case CommonError(name, "", "")       => Json.obj("name" -> name)
      case CommonError(name, reason, "")   => Json.obj("name" -> name, "reason" -> reason)
      case CommonError(name, "", data)     => Json.obj("name" -> name, "data" -> data)
      case CommonError(name, reason, data) => Json.obj("name" -> name, "reason" -> reason, "data" -> data)
    }

    json + ("when" -> JsNumber(error.when))
  }

  override def asString(representation: JsValue): String = representation.toString()

  override def represent(errors: List[ErrorBase]): JsValue = Json.toJson(errors.map(represent))
}
