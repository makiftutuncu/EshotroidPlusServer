package com.mehmetakiftutuncu.utilities

import java.util.concurrent.TimeUnit

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import play.api.Play.current
import play.api.cache.Cache
import play.api.http.{HeaderNames, Status, Writeable}
import play.api.libs.ws.{WSRequest, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.matching.Regex

object Http extends HttpBase {
  override protected def Conf: ConfBase           = com.mehmetakiftutuncu.utilities.Conf
  override protected def WSBuilder: WSBuilderBase = com.mehmetakiftutuncu.utilities.WSBuilder
}

trait HttpBase {
  protected def Conf: ConfBase
  protected def WSBuilder: WSBuilderBase

  val eshotSessionIdCookieName: String = "ASP.NET_SessionId"
  val eshotSessionIdRegex: Regex       = s"""$eshotSessionIdCookieName=(.+);""".r

  lazy val eshotSessionId: Future[String] = {
    get[String](Conf.Hosts.eshotHome, "") {
      wsResponse =>
        val header: String    = wsResponse.header(HeaderNames.SET_COOKIE).getOrElse("")
        val sessionId: String = eshotSessionIdRegex.findFirstMatchIn(header).map(_.group(1)).getOrElse("")

        Right(sessionId)
    } map(_.right.getOrElse(""))
  }

  private def cacheKey(url: String, data: Map[String, Seq[String]] = Map.empty[String, Seq[String]]): String = {
    val dataKey: String = data.map {
      case (key, values) => s"$key=${values.mkString(",")}"
    }.mkString("&")

    s"$url${if (dataKey.isEmpty) "" else "_" + dataKey}"
  }

  def getAsString(url: String): Future[Either[Errors, String]] = {
    val key: String = cacheKey(url)

    val pageFromCacheAsOpt: Option[String] = Cache.getAs[String](key)

    if (pageFromCacheAsOpt.isDefined) {
      Log.debug("Http.getAsString", s"""Hooray! Found page for key "$key" on cache.""")

      Future.successful(Right(pageFromCacheAsOpt.get))
    } else {
      Log.debug("Http.getAsString", s"""Did not find page for key "$key" on cache, proxying request.""")

      eshotSessionId.flatMap {
        sessionId =>
          get[String](url, sessionId) {
            wsResponse =>
              val status: Int = wsResponse.status

              if (status != Status.OK) {
                val errors = Errors(CommonError.requestFailed.reason(s"""Received invalid HTTP status from "$url"!""").data(status.toString))

                Log.error("Http.getAsString", "Received invalid HTTP status!", errors)

                Left(errors)
              } else {
                val result: String = wsResponse.body

                Cache.set(key, result, Conf.Cache.cacheTTLInSeconds)

                Right(result)
              }
          }
      }
    }
  }

  def postFormAsString(url: String, form: Map[String, Seq[String]]): Future[Either[Errors, String]] = {
    val key: String = cacheKey(url, form)

    val pageFromCacheAsOpt: Option[String] = Cache.getAs[String](key)

    if (pageFromCacheAsOpt.isDefined) {
      Log.debug("Http.postFormAsString", s"""Hooray! Found page for key "$key" on cache.""")

      Future.successful(Right(pageFromCacheAsOpt.get))
    } else {
      Log.debug("Http.postFormAsString", s"""Did not find page for key "$key" on cache, proxying request.""")

      eshotSessionId.flatMap {
        sessionId =>
          post[Map[String, Seq[String]], String](url, sessionId, form) {
            wsResponse =>
              val status: Int = wsResponse.status

              if (status != Status.OK) {
                val errors = Errors(CommonError.requestFailed.reason(s"""Received invalid HTTP status from "$url"!""").data(status.toString))

                Log.error("Http.postFormAsString", "Received invalid HTTP status!", errors)

                Left(errors)
              } else {
                val result: String = wsResponse.body

                Cache.set(key, result, Conf.Cache.cacheTTLInSeconds)

                Right(result)
              }
          }
      }
    }
  }

  private def get[R](url: String, sessionId: String)(action: WSResponse => Either[Errors, R]): Future[Either[Errors, R]] = {
    build(url)
      .withHeaders(HeaderNames.COOKIE -> s"$eshotSessionIdCookieName=$sessionId")
      .get().map(wsResponse => action(wsResponse)).recover {
      case t: Throwable =>
        val errors = Errors(CommonError.requestFailed.reason("GET request failed!").data(url))

        Log.error("Http.get", s"""GET request failed!""", errors)

        Left(errors)
    }
  }

  private def post[B, R](url: String, sessionId: String, body: B)(action: WSResponse => Either[Errors, R])(implicit wrt: Writeable[B]): Future[Either[Errors, R]] = {
    build(url)
      .withHeaders(HeaderNames.COOKIE -> s"$eshotSessionIdCookieName=$sessionId")
      .post(body)(wrt).map(wsResponse => action(wsResponse)).recover {
      case t: Throwable =>
        val errors = Errors(CommonError.requestFailed.reason("POST request failed!").data(url))

        Log.error("Http.post", s"""POST request failed!""", errors)

        Left(errors)
    }
  }

  private def build(url: String): WSRequest = {
    WSBuilder.url(url).withRequestTimeout(Duration(Conf.Http.timeoutInSeconds.toLong, TimeUnit.SECONDS))
  }
}
