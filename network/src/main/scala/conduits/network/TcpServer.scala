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
import java.util.concurrent.{TimeUnit, ThreadPoolExecutor, LinkedBlockingQueue}

case class ServerSettings(host: String = "localhost", port: Int = 40096)

object TcpServer {

  implicit val runner: FutureTaskRunner = {
    val numCores = Runtime.getRuntime().availableProcessors()
    val maxPoolsize = 30
    val keepAliveTime = 60000L
    val workQueue = new LinkedBlockingQueue[Runnable]
    val exec = new ThreadPoolExecutor(numCores,
                                      maxPoolsize,
                                      keepAliveTime,
                                      TimeUnit.MILLISECONDS,
                                      workQueue,
                                      new ThreadPoolExecutor.CallerRunsPolicy)
    JavaConversions.asTaskRunner(exec)
  }

  private def forkIO(action: IO[Unit], after: => Unit): IO[Unit] = {
    import scala.concurrent.ops.future
    IO(future {
        try {
          action.unsafePerformIO()
        } finally {
          after
        }
      }(runner).apply())
  }

  def run(server: ServerSettings, app: Application[IO])(implicit MO: MonadIO[IO], MCO: MonadControlIO[IO]): IO[Unit] = {
    def serve(selector: Selector, c: ServerSocketChannel): IO[Unit] = {
//      register(selector, c, SelectionKey.OP_ACCEPT)
      selectOnce[IO](selector, c)(socket => {
        def app1 = MO.bind(app(Network.sourceSocket(socket), Network.sinkSocket(socket)))(_ => MO.point(()))
        IO.controlIO((run: IO.RunInBase[IO, IO]) => {
          forkIO(app1, socket.close()).unsafePerformIO()
          run.apply(IO(()))
        })
      })
    }

    IO.controlIO[IO, Unit]((run: IO.RunInBase[IO, IO]) => {
      val serverChan = Network.bind(new InetSocketAddress(server.host, server.port))
      try {
         IO(Selector.open).flatMap(sel =>
           serverChan.flatMap(chan => {
             register(sel, chan, SelectionKey.OP_ACCEPT)
             run.apply(forever(serve(sel, chan))(MO))
            }
          ))
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

  private def selectOnce[F[_]](selector: Selector, server: ServerSocketChannel)(callback: SocketChannel => F[Unit]): IO[Unit] = {
    IO {
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
          callback(readableChan)
        }
      })
    }
  }

  def forever[F[_], A, B](fa: => F[A])(implicit F: Monad[F]): F[B] = {
    F.bind(fa)(_ => forever(fa))
  }
}
