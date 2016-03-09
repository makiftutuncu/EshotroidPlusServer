package com.mehmetakiftutuncu.controllers

import com.mehmetakiftutuncu.utilities.base.ControllerBase
import play.api.mvc._

class Application extends ControllerBase {
  def test = Action {
    okWithText("Eshotroid+ Server is up and running!")
  }
}
