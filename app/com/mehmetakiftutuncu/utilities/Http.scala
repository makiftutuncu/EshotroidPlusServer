package com.mehmetakiftutuncu.utilities

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import play.api.http.{Writeable, Status}
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Http extends HttpBase {
  override protected def Conf: ConfBase           = com.mehmetakiftutuncu.utilities.Conf
  override protected def WSBuilder: WSBuilderBase = com.mehmetakiftutuncu.utilities.WSBuilder
}

trait HttpBase {
  protected def Conf: ConfBase
  protected def WSBuilder: WSBuilderBase

  def getAsString(url: String): Future[Either[Errors, String]] = {
    get[String](url) {
      wsResponse =>
        val status: Int = wsResponse.status

        if (status != Status.OK) {
          val errors = Errors(CommonError.requestFailed.reason(s"""Received invalid HTTP status from "$url"!""").data(status.toString))

          Log.error("Http.getAsString", "Received invalid HTTP status!", errors)

          Left(errors)
        } else {
          Right(wsResponse.body)
        }
    }
  }

  def postFormAsString(url: String, form: Map[String, Seq[String]]): Future[Either[Errors, String]] = {
    post[Map[String, Seq[String]], String](url, form) {
      wsResponse =>
        val status: Int = wsResponse.status

        if (status != Status.OK) {
          val errors = Errors(CommonError.requestFailed.reason(s"""Received invalid HTTP status from "$url"!""").data(status.toString))

          Log.error("Http.postFormAsString", "Received invalid HTTP status!", errors)

          Left(errors)
        } else {
          Right(wsResponse.body)
        }
    }
  }

  private def get[R](url: String)(action: WSResponse => Either[Errors, R]): Future[Either[Errors, R]] = {
    build(url).get().map(wsResponse => action(wsResponse)).recover {
      case t: Throwable =>
        val errors = Errors(CommonError.requestFailed.reason("GET request failed!").data(url))

        Log.error("Http.get", s"""GET request failed!""", errors)

        Left(errors)
    }
  }

  private def post[B, R](url: String, body: B)(action: WSResponse => Either[Errors, R])(implicit wrt: Writeable[B]): Future[Either[Errors, R]] = {
    build(url).post(body)(wrt).map(wsResponse => action(wsResponse)).recover {
      case t: Throwable =>
        val errors = Errors(CommonError.requestFailed.reason("POST request failed!").data(url))

        Log.error("Http.post", s"""POST request failed!""", errors)

        Left(errors)
    }
  }

  private def build(url: String): WSRequest = {
    WSBuilder.url(url).withRequestTimeout(Conf.Http.timeoutInSeconds.toLong * 1000)
  }
}
