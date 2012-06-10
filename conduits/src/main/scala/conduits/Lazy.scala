package conduits

import pipes._
import resourcet.MonadActive
import conduits.Pipe._
import collection.immutable.Stream
import scalaz.effect.{MonadControlIO, IO}
import IO._

object Lazy {

  /**
   * Use lazy IO to consume all elements from a Source.
   * It relies on MonadActive to determine if the underlying
   * Monadic state has been closed.
   */
  def lazyConsume[F[_], A](s: Source[F, A])(implicit M: MonadControlIO[F], MA: MonadActive[F]): F[Stream[A]] =
    s match {
      case Done(()) => M.point(Stream.Empty)
      case HaveOutput(src, _, x) => M.map(lazyConsume(src))(xs => x #:: xs)
      case PipeM(msrc, _) =>
        controlIO((runInIO: RunInBase[F, IO]) => {
          for {
            r1 <- IO(M.bind(MA.monadActive)(a => {
                      if (a) M.bind(msrc)(s => lazyConsume(s))
                      else M.point(Stream.empty[A])
                    })).unsafeInterleaveIO
            r2 <- io(rw => r1.map(c => rw -> c))
          } yield r2
        })
      case NeedInput(_, c) => lazyConsume(c)
      case Leftover(p, i) => lazyConsume(p.pipePushStrip(i))
    }

}
