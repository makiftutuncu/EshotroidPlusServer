package com.mehmetakiftutuncu.utilities

import play.api.Play.current
import play.api.cache.{Cache => PlayCache}
import play.api.libs.json.JsValue

object Cache extends CacheBase

trait CacheBase {
  def getJson(key: String): Option[JsValue] = PlayCache.getAs[JsValue](key)

  def setJson(key: String, data: JsValue): Unit = PlayCache.set(key, data)
}
