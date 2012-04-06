package conduits
package binary

import pipes._
import Pipe._
import SourceFunctions._
import ConduitFunctions._
import SinkFunctions._
import scalaz.effect.IO
import java.io.{FileOutputStream, FileInputStream, File}
import java.nio.channels.FileChannel
import resourcet.{ReleaseKey, MonadResource}

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

  def sourceFileRange[F[_]](f: File, offset: Option[Int] = None, count: Option[Int] = None, chunkSize: Int = byteString.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] = {
    val M = MR.MO
    def pullUnlimited(c: FileChannel, key: ReleaseKey): F[Source[F, ByteString]] =
      MR.MO.bind(MR.MO.liftIO(byteString.getContents(c)))(bs =>
        if (bs.isEmpty) M.map(MR.release(key))(_ => Done[Zero, ByteString, F, Unit](None, ()))
        else M.point(HaveOutput(PipeM(pullUnlimited(c, key), MR.release(key)), MR.release(key), bs))
      )

    def pullLimited(i: Int, fc: FileChannel, key: ReleaseKey): F[Source[F, ByteString]] = {
      val c = math.min(i, byteString.DefaultChunkSize)
      MR.MO.bind(MR.MO.liftIO(byteString.getContents(fc, c)))(bs => {
        val c1 = c - bs.length
        if (bs.isEmpty) M.map(MR.release(key))(_ => Done[Zero, ByteString, F, Unit](None, ()))
        else M.point(HaveOutput(PipeM(pullLimited(c1, fc, key), MR.release(key)), MR.release(key), bs))
      })
    }

    PipeM(M.bind(MR.allocate[FileChannel](IO(new FileInputStream(f).getChannel), c => IO(c.close)))(kh => {
      val key = kh._1
      val chan = kh._2
      M.bind(offset.map(off => M.liftIO(IO(kh._2.position(off)))) getOrElse M.point(()))(_ =>
        count.map(o => pullLimited(o, chan, key)) getOrElse pullUnlimited(chan, key))
    }), M.point(()))
  }

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
