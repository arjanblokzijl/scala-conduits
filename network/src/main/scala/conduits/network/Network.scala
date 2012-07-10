package conduits
package network

import empty.Void
import pipes._
import java.net.{InetSocketAddress, Socket}
import scalaz.effect.{IO, MonadIO}
import bs.ByteString
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import conduits.Pipe._
import Finalize._
import scalaz.Monad
import java.util.{Date, GregorianCalendar}

/**
 * User: arjan
 */

object Network {


  def sourceSocket[F[_]](chan: SocketChannel)(implicit F: MonadIO[F]): Source[F, ByteString] = {
    val close: Finalize[F, Unit] = FinalizePure(())
    def push: F[Source[F, ByteString]] =
      F.map(F.liftIO(ByteString.getContents(chan)))(bs =>
        if (bs.isEmpty) Done()
        else HaveOutput(src, close, bs)
      )
    def src = PipeM[Void, ByteString, F, Unit](push, close)

    PipeM[Void, ByteString, F, Unit](push, close)
  }

  def sinkSocket[F[_]](chan: SocketChannel)(implicit F: MonadIO[F]): Sink[ByteString, F, Unit] = {
    def sink = NeedInput[ByteString, Void, F, Unit](push, Done())
    def push: ByteString => Sink[ByteString, F, Unit] = bs =>
      PipeM(F.map(F.liftIO(ByteString.writeContents(chan, bs)))(_ => sink), FinalizePure(()))

    sink
  }

  def bind(adr: InetSocketAddress): IO[ServerSocketChannel] =
    IO {
      val chan = ServerSocketChannel.open()
      chan.socket().bind(adr)
      chan
    }

  type Application[F[_]] = (Source[F, ByteString], Sink[ByteString, F, Unit]) => F[Unit]
}

