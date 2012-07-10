package conduits
package network

import scalaz.effect.{MonadControlIO, MonadIO, IO}
import java.nio.channels.SocketChannel
import java.net.InetSocketAddress

/**
 * User: arjan
 */

object TcpClient {

  case class ClientSettings(host: String, port: Int = 4096)

  def runClient[F[_]](client: ClientSettings, app: Network.Application[F])(implicit MO: MonadIO[F], MCO: MonadControlIO[F]): F[Unit] = {
//    IO.controlIO[F, Unit]((run: IO.RunInBase[F, IO]) => {
//      socket(client.host, client.port).bracket(c => IO(c.close))(chan => run.apply(app(Network.sourceSocket(chan), Network.sinkSocket(chan))))
//    })
    IO.controlIO[F, Unit]((run: IO.RunInBase[F, IO]) => {
      socket(client.host, client.port).flatMap(chan => run.apply(app(Network.sourceSocket(chan), Network.sinkSocket(chan))))
    })
  }

  def socket(host: String, port: Int): IO[SocketChannel] = {
    IO {
      val chan = SocketChannel.open
      chan.connect(new InetSocketAddress(host, port))
      chan
    }
  }
}
