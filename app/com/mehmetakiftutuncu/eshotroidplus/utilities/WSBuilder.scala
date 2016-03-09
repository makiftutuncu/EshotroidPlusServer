package com.mehmetakiftutuncu.eshotroidplus.utilities

import play.api.Application
import play.api.libs.ws.{WS, WSRequest}

object WSBuilder extends WSBuilderBase

trait WSBuilderBase {
  def url(url: String)(implicit app: Application): WSRequest = WS.url(url)
}
