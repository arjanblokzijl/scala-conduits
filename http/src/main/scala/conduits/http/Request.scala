package conduits
package http

import javax.servlet.http.HttpServletRequest
import bs.ByteString

/**
 * User: arjan
 */

sealed trait HttpRequest[A] {

  def uri: URI

  def method: RequestMethod

  def body: RequestBody[A]
}


sealed trait RequestBody[A]

object HttpRequest {

  private val HttpPrefix = "http://"
  def httpGet(url: String): HttpRequest[ByteString] = {
    val httpUrl = if (url.startsWith(HttpPrefix)) url
                  else HttpPrefix + url
    http(httpUrl, RequestMethod.GET)
  }

  def http(url: String, method: RequestMethod) = new HttpRequest[ByteString] {
    def body = RequestBody.RequestBodyByteBS(ByteString.empty)

    def method = method

    def uri = URI.uri(url)
  }

}

object RequestBody {
  case class RequestBodyByteBS(bs: ByteString) extends RequestBody[ByteString]
}
