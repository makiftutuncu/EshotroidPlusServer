package com.mehmetakiftutuncu.controllers

import com.mehmetakiftutuncu.utilities.base.ControllerBase
import com.mehmetakiftutuncu.utilities.{Conf, Http}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class Application extends ControllerBase {
  def index = Action {
    Ok("Hello world!")
  }

  def test = Action.async {
    Http.getAsString(Conf.Hosts.eshotHome).map {
      case Left(errors)         => okWithError(errors)
      case Right(eshotHomePage) => okWithHtml(eshotHomePage)
    }
  }
}
