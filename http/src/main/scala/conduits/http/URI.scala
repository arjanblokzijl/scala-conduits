package conduits
package http


/**
 * User: arjan
 */

sealed trait URI {

  private[http] def uri: java.net.URI
  def query: Option[String] = Option(uri.getQuery)

  def path: String = uri.getPath

  def host: String = uri.getHost

  def port: Int = uri.getPort

  def asUrl: java.net.URL = uri.toURL

}

case class URIAuth(userInfo: String, regName: String, port: String)

object URI {
  def apply(u: java.net.URI) = new URI {
    val uri = u
  }
  def uri(s: String) = new URI {
    val uri = new java.net.URI(s)
  }
}
