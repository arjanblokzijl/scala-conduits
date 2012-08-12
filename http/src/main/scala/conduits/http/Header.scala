package conduits
package http

import HttpStream._
import scala.annotation._
case class Header(name: HeaderName, value: String)

sealed trait HasHeaders[A] {
  def getHeaders(a: A): List[Header]
  def setHeaders(a: A, headers: List[Header]): A
}

object Headers {
  type HeaderSetter[A] = (HeaderName, String, A) => A

  /**
   * Inserts a header with the given header name and value.
   * Does not check for existing headers with the same name,
   * allowing duplicates to be introduced.
   */
  def insertHeader[A](implicit H: HasHeaders[A]): HeaderSetter[A] = (name, value, a) =>
    H.setHeaders(a, Header(name, value) +: H.getHeaders(a))


  def insertHeaders[A](hdrs: Seq[Header], a: A)(implicit H: HasHeaders[A]): A =
    H.setHeaders(a, H.getHeaders(a) ++ hdrs)

  /**
   * Replaces the header with the given value replacing existing values with the same name.
   */
  def replaceHeader[A](implicit H: HasHeaders[A]): HeaderSetter[A] = (name, value, a) => {
    def newHeaders = Header(name, value) +: H.getHeaders(a).filter(_.name != name)
    H.setHeaders(a, newHeaders)
  }

  /**
   * Gets a list of headers with the given `HeaderName`.
   */
  def replaceHeader[A](name: HeaderName, a: A)(implicit H: HasHeaders[A]): List[Header] =
    H.getHeaders(a).filter(_.name == name)

  /**Looks up the header with the given name, returning the first header that matches, if any.*/
  def findHeader[A](name: HeaderName, a: A)(implicit H: HasHeaders[A]): Option[String] =
    lookupHeader(name, H.getHeaders(a))

  @tailrec
  def lookupHeader[A](name: HeaderName, hdrs: List[Header])(implicit H: HasHeaders[A]): Option[String] = hdrs match {
    case Nil => None
    case h :: hs => if (name == h.name) Some(h.value)
                    else lookupHeader(name, hs)
  }

  def parseHeader(str: String): Result[Header] = {
    def mat(s1: String, s2: String): Boolean =
      s1.toLowerCase == s2.toLowerCase

    def fn(k: String): HeaderName = {
      val header = HeaderName.headerMap.filter{case (fst, snd) => mat(k, fst)}
      if (header.isEmpty) HeaderName.HdrCustom(k)
      else header.head._2
    }

    val (l, r) = str.span(_ != ':')
    if (l.isEmpty || r.isEmpty) failParse("unable to parse header " + str)
    else Right(Header(fn(l), r.drop(1).trim))
  }

}