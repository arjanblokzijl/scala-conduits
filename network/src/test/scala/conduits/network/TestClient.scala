package conduits
package network

import network.TcpClient.ClientSettings
import conduits.pipes._
import network.TcpClient.ClientSettings
import bs.ByteString
import Conduits._
import scalaz.Monad
import conduits.Pipe.Done
import resourcet._
import scalaz.effect.IO
import empty.Void

/**
 * User: arjan
 */

object TestClient extends App {

  import monadControlIO._

  def client(src: Source[IO, ByteString], sink: Sink[ByteString, IO, Unit]): IO[Unit] = {
    def conduit: Conduit[ByteString, IO, ByteString] = {
      yieldp[ByteString, ByteString, IO](ByteString.fromString("hello world " + Thread.currentThread().getId))
        .flatMap(_ => await[IO, ByteString, ByteString]
        .flatMap(bs =>
        pipeMonadTrans[ByteString, ByteString].liftM(IO.putStrLn("EchoClient %s received: %s" format(Thread.currentThread().getId, bs.getOrElse(ByteString.empty).toString)))
      ))
    }

    src &= conduit =% sink
  }

  class ClientRunner extends Runnable {
    def run() = TcpClient.runClient[IO](ClientSettings("localhost", 4096), client).unsafePerformIO
  }

  (1 to 20) foreach(_ => new Thread(new ClientRunner).start())
}

