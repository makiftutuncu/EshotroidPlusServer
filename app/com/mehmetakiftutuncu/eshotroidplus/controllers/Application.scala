package com.mehmetakiftutuncu.eshotroidplus.controllers

import com.mehmetakiftutuncu.eshotroidplus.utilities.base.ControllerBase
import play.api.mvc._

class Application extends ControllerBase {
  def test = Action {
    okWithText("Eshotroid+ Server is up and running!")
  }
}
