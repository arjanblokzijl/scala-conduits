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
import scalaz.Monad
import binary.{ByteString => B}
import empty.Void
import Finalize._

/**
 * User: arjan
 */

object Binary {

  val bufferSize = 8*1024

  def sourceFile[F[_]](f: File, chunkSize: Int = B.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIOStream(IO(new FileInputStream(f)))

  private def sourceIOStream[F[_]](alloc: IO[FileInputStream], chunkSize: Int = ByteString.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIO[F, ByteString, java.io.FileInputStream](alloc, s => IO(s.close()), s => {
      MR.MO.map(MR.MO.liftIO(ByteString.getContents(s.getChannel, chunkSize)))(bs =>
        if (bs.isEmpty) IOClosed.apply
        else IOOpen(bs))
    })

  def sourceFileRange[F[_]](f: File, offset: Option[Int] = None, count: Option[Int] = None, chunkSize: Int = ByteString.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] = {
    val M = MR.MO
    def pullUnlimited(c: FileChannel, key: ReleaseKey): F[Source[F, ByteString]] =
      MR.MO.bind(MR.MO.liftIO(ByteString.getContents(c)))(bs =>
        if (bs.isEmpty) M.map(MR.release(key))(_ => Done(None, ()))
        else M.point(HaveOutput(PipeM(pullUnlimited(c, key), FinalizeM(MR.release(key))), FinalizeM(MR.release(key)), bs))
      )

    def pullLimited(i: Int, fc: FileChannel, key: ReleaseKey): F[Source[F, ByteString]] = {
      val c = math.min(i, B.DefaultChunkSize)
      MR.MO.bind(MR.MO.liftIO(B.getContents(fc, c)))(bs => {
        val c1 = c - bs.length
        if (bs.isEmpty) M.map(MR.release(key))(_ => Done(None, ()))
        else M.point(HaveOutput(PipeM(pullLimited(c1, fc, key),  FinalizeM(MR.release(key))), FinalizeM(MR.release(key)), bs))
      })
    }

    PipeM(M.bind(MR.allocate[FileChannel](IO(new FileInputStream(f).getChannel), c => IO(c.close)))(kh => {
      val key = kh._1
      val chan = kh._2
      M.bind(offset.map(off => M.liftIO(IO(kh._2.position(off)))) getOrElse M.point(()))(_ =>
        count.map(o => pullLimited(o, chan, key)) getOrElse pullUnlimited(chan, key))
    }), FinalizePure(()))
  }

  def sinkFile[F[_]](f: File)(implicit MR: MonadResource[F]): Sink[ByteString, F, Unit] =
    sinkIOStream(IO(new FileOutputStream(f)))

  private def sinkIOStream[F[_]](fs: IO[FileOutputStream])(implicit MR: MonadResource[F]): Sink[ByteString, F, Unit] = {
    implicit val M = MR.MO
    sinkIO[F, ByteString, Unit, java.io.FileOutputStream](fs, s => IO(s.close), f => bs =>
      M.map(M.liftIO(bs.writeContents(f)))(_ => IOProcessing.apply), _ => M.point(()))
  }

  def conduitFile[F[_]](f: File)(implicit MR: MonadResource[F]): Conduit[ByteString, F, ByteString] =
    conduitStream(IO(new FileOutputStream(f)))

  private def conduitStream[F[_]](fs: IO[FileOutputStream])(implicit MR: MonadResource[F]): Conduit[ByteString, F, ByteString] = {
    implicit val M = MR.MO
    conduitIO[F, ByteString, ByteString, java.io.FileOutputStream](fs, s => IO(s.close), f => bs =>
      M.map(M.liftIO(bs.writeContents(f)))(_ => IOProducing(Stream(bs))), _ => M.point(Stream.empty))
  }

  def head[F[_]](implicit M: Monad[F]): Sink[ByteString, F, Option[Byte]] = {
    def push(bs: ByteString): Sink[ByteString, F, Option[Byte]] = bs.uncons match {
      case None => NeedInput(push, close)
      case Some((b, bs1)) => Done(if (bs1.isEmpty) None else Some(bs1), Some(b))
    }
    def close: Sink[ByteString, F, Option[Byte]] = pipeMonad[ByteString, Void, F].point(None)
    NeedInput(push, close)
  }

  /**
   * Ensure that the inner sink consumes maximally the given number of bytes.
   * This does not ensure that all of those bytes are actually consumed.
   */
  def isolate[F[_]](count: Int)(implicit M: Monad[F]): Conduit[ByteString, F, ByteString] = {
    def close(s: => Int): F[Stream[ByteString]] = M.point(Stream.Empty)
    def push(c: => Int, bs: => ByteString): F[ConduitStateResult[Int, ByteString, ByteString]] =
      if (c <= 0) M.point(StateFinished(Some(bs), Stream.Empty))
      else {
        val (a, b) = bs.splitAt(c)
        val c1 = c - a.length
        M.point(if (c1 <= 0) StateFinished(if (b.isEmpty) None else Some(b), if (a.isEmpty) Stream.Empty else Stream(a))
        else StateProducing(c1, Stream(a)))
      }
    conduitState(count, push, close)
  }

  /**Return all bytes while the given predicate is true.*/
  def takeWhile[F[_]](p: Byte => Boolean)(implicit M: Monad[F]): Conduit[ByteString, F, ByteString] = {
    def close: Conduit[ByteString, F, ByteString] = pipes.pipeMonoid[ByteString, ByteString, F].zero
    def push(bs: ByteString): Conduit[ByteString, F, ByteString] = {
      val (x, y) = bs.span(p)
      if (bs.isEmpty) {
        val r = NeedInput(push, close)
        if (x.isEmpty) r
        else HaveOutput(r, FinalizePure(()), x)
      }
      else {
        val f = Done[ByteString, ByteString, F, Unit](Some(y), ())
        if (x.isEmpty) f
        else HaveOutput(f, FinalizePure(()), x)
      }
    }
    NeedInput(push, close)
  }

  /**Drop all bytes while the given predicate is true.*/
  def dropWhile[F[_]](p: Byte => Boolean)(implicit M: Monad[F]): Sink[ByteString, F, Unit] = {
    def close: Sink[ByteString, F, Unit] = pipes.pipeMonoid[ByteString, Void, F].zero
    def push(bs: ByteString): Sink[ByteString, F, Unit] = {
      val bs1 = bs.dropWhile(p)
      if (bs1.isEmpty) NeedInput(push, close)
      else Done(Some(bs1), ())
    }
    NeedInput(push, close)
  }

  /**Split a ByteString into lines, where Byte '10' represents the LF Byte.*/
  def lines[F[_]](implicit M: Monad[F]): Conduit[ByteString, F, ByteString] = {
    import scalaz.std.stream._
    def push[S](sofar: ByteString => ByteString)(more: ByteString): Conduit[ByteString, F, ByteString] = {
      val (first, second) = more.span(_ != 10.toByte)
      second.uncons match {
        case Some((_, second1)) => HaveOutput(push(identity)(second1), FinalizePure(()), sofar(first))
        case None => {
          val rest = sofar(more)
          NeedInput(push(rest.append _), close(rest))
        }
      }
    }

    def close(rest: ByteString): Conduit[ByteString, F, ByteString] =
      if (rest.isEmpty) Done(None, ())
      else HaveOutput(Done(None, ()), FinalizePure(()), rest)

    NeedInput(push(identity), close(ByteString.empty))
  }
}
