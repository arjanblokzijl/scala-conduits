package conduits
package http

import bs.ByteString
import scalaz.effect.IO


sealed trait Connection {

  private[http] def conn: IO[java.net.URLConnection]

  def execute: IO[ByteString] =
    conn.flatMap(c => ByteString.fromInputStream(c.getInputStream))
}

object Connection {
  def apply[A](request: HttpRequest[A]) = new Connection {
    val conn = IO(request.uri.asUrl.openConnection)
  }
}
