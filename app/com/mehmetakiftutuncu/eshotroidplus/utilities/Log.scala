package com.mehmetakiftutuncu.eshotroidplus.utilities

import com.github.mehmetakiftutuncu.errors.Errors
import play.api.Logger

object Log {
  def debug(tag: => String, message: => String) = {
    val log = s"""[$tag] $message"""

    Logger.debug(log)
  }

  def warn(tag: => String, message: => String) = {
    val log = s"""[$tag] $message"""

    Logger.warn(log)
  }

  def error(tag: => String, message: => String) = {
    val log = s"""[$tag] $message"""

    Logger.error(log)
  }

  def error(tag: => String, message: => String, errors: Errors) = {
    val log = s"""[$tag] $message Errors: ${errors.represent}"""

    Logger.error(log)
  }

  def error(t: Throwable, tag: => String, message: => String) = {
    val log = s"""[$tag] $message"""

    Logger.error(log, t)
  }

  def error(t: Throwable, tag: => String, message: => String, errors: Errors) = {
    val log = s"""[$tag] $message Errors: ${errors.represent}"""

    Logger.error(log, t)
  }
}
