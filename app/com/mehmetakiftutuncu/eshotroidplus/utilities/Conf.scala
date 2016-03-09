package com.mehmetakiftutuncu.eshotroidplus.utilities

import play.api.Play

object Conf extends ConfBase

trait ConfBase {
  object Cache {
    val cacheTTLInSeconds: Int = getConfInt("eshotroidplus.cache.cacheTTL", 86400)
  }

  object Http {
    val timeoutInSeconds: Int = getConfInt("eshotroidplus.http.timeout", 10)
  }

  object Database {
    val timeoutInSeconds: Int = getConfInt("eshotroidplus.database.timeout", 5)
  }

  object Hosts {
    val eshotHome: String = getConfString("eshotroidplus.hosts.eshotHome", "")
    val busPage: String   = getConfString("eshotroidplus.hosts.busPage", "")
  }

  def getConfInt(key: String, defaultValue: Int): Int = {
    Play.maybeApplication.flatMap(_.configuration.getInt(key)).getOrElse(defaultValue)
  }

  def getConfString(key: String, defaultValue: String): String = {
    Play.maybeApplication.flatMap(_.configuration.getString(key)).getOrElse(defaultValue)
  }
}
