package conduits
package binary

import pipes._
import Pipe._
import SourceFunctions._
import ConduitFunctions._
import SinkFunctions._
import scalaz.effect.IO
import resourcet.MonadResource
import java.io.{FileOutputStream, FileInputStream, File}

/**
 * User: arjan
 */

object Binary {

  val bufferSize = 8*1024

  def sourceFile[F[_]](f: File, chunkSize: Int = byteString.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIOStream(IO(new FileInputStream(f)))

  private def sourceIOStream[F[_]](alloc: IO[FileInputStream], chunkSize: Int = byteString.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIO[F, ByteString, java.io.FileInputStream](alloc, s => IO(s.close()), s => {
      MR.MO.map(MR.MO.liftIO(byteString.getContents(s.getChannel, chunkSize)))(bs =>
        if (bs.isEmpty) IOClosed.apply
        else IOOpen(bs))
    })

  def sinkFile[F[_]](f: File)(implicit MR: MonadResource[F]): Sink[ByteString, F, Unit] =
    sinkIOStream(IO(new FileOutputStream(f)))

  private def sinkIOStream[F[_]](fs: IO[FileOutputStream])(implicit MR: MonadResource[F]): Sink[ByteString, F, Unit] = {
    implicit val M = MR.MO
    sinkIO[F, ByteString, Unit, java.io.FileOutputStream](fs, s => IO(s.close), f => bs =>
      M.map(M.liftIO(bs.writeContents(f.getChannel)))(_ => IOProcessing.apply), _ => M.point(()))
  }

  def conduitFile[F[_]](f: File)(implicit MR: MonadResource[F]): Conduit[ByteString, F, ByteString] =
    conduitStream(IO(new FileOutputStream(f)))

  private def conduitStream[F[_]](fs: IO[FileOutputStream])(implicit MR: MonadResource[F]): Conduit[ByteString, F, ByteString] = {
    implicit val M = MR.MO
    conduitIO[F, ByteString, ByteString, java.io.FileOutputStream](fs, s => IO(s.close), f => bs =>
      M.map(M.liftIO(bs.writeContents(f.getChannel)))(_ => IOProducing(Stream(bs))), _ => M.point(Stream.empty))
  }
}
