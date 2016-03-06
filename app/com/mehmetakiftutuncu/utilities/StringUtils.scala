package com.mehmetakiftutuncu.utilities

import java.util.Locale

object StringUtils {
  def sanitizeHtml(t: String, capitalizeEachWord: Boolean = true): String = {
    val locale = new Locale("tr")

    val result = {
      t.trim.replaceAll("&#304;", "İ")
            .replaceAll("&#214;", "Ö")
            .replaceAll("&#220;", "Ü")
            .replaceAll("&#199;", "Ç")
            .replaceAll("&#286;", "Ğ")
            .replaceAll("&#350;", "Ş")
            .replaceAll("&#305;", "ı")
            .replaceAll("&#246;", "ö")
            .replaceAll("&#252;", "ü")
            .replaceAll("&#231;", "ç")
            .replaceAll("&#287;", "ğ")
            .replaceAll("&#351;", "ş")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("\\.", ". ")
            .replaceAll("-", " - ")
            .replaceAll("\\s\\s+", " ")
    }

    if (capitalizeEachWord) {
      result.toLowerCase(locale).split(" ").map(w => w.take(1).toUpperCase(locale) + w.drop(1)).mkString(" ")
    } else {
      result
    }
  }
}
