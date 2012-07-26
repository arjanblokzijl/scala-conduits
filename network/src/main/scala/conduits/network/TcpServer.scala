package conduits
package network

/**
 * User: arjan
 */
import Network._
import scalaz.effect._
import java.net.InetSocketAddress
import java.nio.channels._
import scala.collection.JavaConverters._
import scalaz.Monad
import concurrent.{JavaConversions, FutureTaskRunner}
import java.util.concurrent._
import scalaz.concurrent.Strategy

case class ServerSettings(host: String = "localhost", port: Int = 40096)

object TcpServer {

  private val queue = new LinkedBlockingQueue[SocketChannel]()

  private def forkIO(action: IO[Unit], after: => Unit)(implicit S: Strategy): IO[Unit] =
    IO(S(try {
          action unsafePerformIO
          ()
        } finally {
          after
        }
      ))


  def run(server: ServerSettings, app: Application[IO])(implicit MO: MonadIO[IO], MCO: MonadControlIO[IO], S: Strategy = TcpServerStrategy.DefaultServerStrategy): IO[Unit] = {
    def serve(selector: Selector, c: ServerSocketChannel): IO[Unit] = {
      val socket = queue.take()
      IO.controlIO((run: IO.RunInBase[IO, IO]) => {
        def app1 = MO.bind(app(Network.sourceSocket(socket), Network.sinkSocket(socket)))(_ => MO.point(()))
        forkIO(app1, socket.close()).flatMap(_ => run.apply(IO(())))
      })
    }

    IO.controlIO[IO, Unit]((run: IO.RunInBase[IO, IO]) => {
      val serverChan = Network.bind(new InetSocketAddress(server.host, server.port))
      try {
        IO(Selector.open).flatMap(sel =>
          serverChan.flatMap(chan => {
            register(sel, chan, SelectionKey.OP_ACCEPT)
            NioSelector.select(sel, chan).flatMap(_ => run.apply(forever(serve(sel, chan))(MO)))}))
      } finally {
        serverChan.map(_.close).unsafePerformIO()
      }
    })
  }

  private def register(sel: Selector, chan: SelectableChannel, ops: Int): Unit = {
    if (chan != null) {
      chan.configureBlocking(false)
      chan.register(sel, ops)
    }
  }

  def forever[F[_], A, B](fa: => F[A])(implicit F: Monad[F]): F[B] = {
    F.bind(fa)(_ => forever(fa))
  }

  object NioSelector {
    def select(selector: Selector, server: ServerSocketChannel): IO[Unit] = {
      IO(new Thread(new NioSelector(selector, server)).start)
    }
  }

  private class NioSelector(selector: Selector, server: ServerSocketChannel) extends Runnable {
    def run() {
      doSelect.unsafePerformIO
    }

    private def doSelect: IO[Unit] = {
      def select: IO[Unit] = IO {
       register(selector, server, SelectionKey.OP_ACCEPT)
       def go: Unit = {
        val n = selector.select
        val keys = selector.selectedKeys.asScala.toList
        selector.selectedKeys().clear()
        keys.foreach(key => {
          if (key.isAcceptable) {
            val server = key.channel.asInstanceOf[ServerSocketChannel]
            val chan = server.accept
            register(selector, chan, SelectionKey.OP_READ)
          }
          if (key.isReadable) {
            val readableChan = key.channel.asInstanceOf[SocketChannel]
            key.cancel //will be handled in a different thread in TCP server, so don't read it again in the next loop
            queue.put(readableChan)
          }
        })
        go
       }
       go
      }
      select
    }
  }
}