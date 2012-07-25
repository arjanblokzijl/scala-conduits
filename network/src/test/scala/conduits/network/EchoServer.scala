package conduits
package network

import pipes._
import bs.ByteString
import Conduits._
import resourcet.monadControlIO
import scalaz.effect.IO
import scalaz.Monad
import java.net.InetAddress

object EchoServer extends App {

  val host = if (args.length > 1) args(0) else "127.0.0.1"

  val port = if (args.length > 1) args(1).toInt else if (args.length > 0) args(0).toInt else 4096

  import monadControlIO._

  def echo[F[_]](src: Source[F, ByteString], sink: Sink[ByteString, F, Unit])(implicit F: Monad[F]) = src &= sink

  TcpServer.run(ServerSettings(host, port), echo[IO]).unsafePerformIO

}
