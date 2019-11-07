package org.ieee_passau.utils

import java.nio.charset.Charset
import org.apache.commons.codec.binary.Base64

object StringHelper {

  /**
    * Remove certain characters from a string.
    *
    * @param s            String to remove characters from.
    * @param charsToStrip Set of characters to remove from string.
    * @return s with all characters of charsToStrip removed.
    */
  def stripChars(s: String, charsToStrip: String): String =
    s.filterNot(c => charsToStrip.contains(c))

  def stripNull: Option[String] => Option[String] =
    (x: Option[String]) => if (x.isDefined) Some(stripChars(x.get, "\u0000")) else None

  def base64Encode(data: String): String = {
    Base64.encodeBase64String(data.getBytes(Charset.forName("UTF-8")))
  }

  def base64Decode(data: String): String = {
    new String(Base64.decodeBase64(data), Charset.forName("UTF-8"))
  }

  def cleanNewlines(data: String): String = {
    data.replace("\r\n", "\n").replace("\r", "\n")
  }

  def encodeEmailName(name: String): String = {
    name.replace("<", "〈").replace(">", "〉")
  }
}
