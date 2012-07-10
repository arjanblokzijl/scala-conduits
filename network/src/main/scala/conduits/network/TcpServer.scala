package conduits
package network

/**
 * User: arjan
 */
import Network._
import scalaz.effect._
import java.net.InetSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import scalaz.syntax.bind._
import resourcet.IOUtils
import scalaz.Monad

object TcpServer {

  case class ServerSettings(host: String = "localhost", port: Int = 40096)

  //TODO Simple, single threaded blocking TCP Server just for illustration purposes. Implement using NIO selectors.
  def runServer(server: ServerSettings, app: Application[IO])(implicit MO: MonadIO[IO], MCO: MonadControlIO[IO]): IO[Unit] = {
    def serve(c: ServerSocketChannel): IO[Unit] = {
      IO(c.accept()).flatMap(socket => {
        def app1 = MO.bind(app(Network.sourceSocket(socket), Network.sinkSocket(socket)))(_ => MO.point(()))
        try {
          app1
        } finally {
          IO(socket.close())
        }})
    }
    IO.controlIO[IO, Unit]((run: IO.RunInBase[IO, IO]) => {
      val serverSocket = Network.bind(new InetSocketAddress(server.host, server.port))
      try {
        //TODO fixme: should work using bracket.
        serverSocket.flatMap(chan => run.apply(forever(serve(chan))(MO)))
      } finally {
        serverSocket.map(_.close())
      }
    })
  }

  def forever[F[_], A, B](fa: => F[A])(implicit F: Monad[F]): F[B] = {
    F.bind(fa)(_ => forever(fa))
  }
}
