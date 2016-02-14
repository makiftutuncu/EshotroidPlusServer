package com.mehmetakiftutuncu.utilities.base

import com.github.mehmetakiftutuncu.errors.Errors
import com.mehmetakiftutuncu.utilities.JsonErrorRepresenter
import play.api.http.ContentTypes
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Controller, Result}

import scala.concurrent.Future

trait ControllerBase extends Controller {
  def okWithError(errors: Errors): Result = {
    Ok(Json.obj("errors" -> errors.represent(JsonErrorRepresenter))).as(ContentTypes.JSON)
  }

  def okWithJson(json: JsValue): Result = {
    Ok(Json.obj("success" -> json)).as(ContentTypes.JSON)
  }

  def okWithText(text: String): Result = {
    Ok(text).as(ContentTypes.TEXT)
  }

  def okWithHtml(html: String): Result = {
    Ok(html).as(ContentTypes.HTML)
  }

  def futureOkWithError(errors: Errors): Future[Result] = {
    Future.successful(okWithError(errors))
  }

  def futureOkWithJson(json: JsValue): Future[Result] = {
    Future.successful(okWithJson(json))
  }

  def futureOkWithText(text: String): Future[Result] = {
    Future.successful(okWithText(text))
  }

  def futureOkWithHtml(html: String): Future[Result] = {
    Future.successful(okWithHtml(html))
  }
}
