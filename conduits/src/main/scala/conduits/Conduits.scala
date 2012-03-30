package conduits

/**
* User: arjan
*/
import scalaz.Monad
import pipes._
import Pipe._

trait ConduitsFunctions {

//  -- | The connect operator, which pulls data from a source and pushes to a sink.
//  -- There are two ways this process can terminate:
//  --
//  -- 1. If the @Sink@ is a @Done@ constructor, the @Source@ is closed.
//  --
//  -- 2. If the @Source@ is a @Done@ constructor, the @Sink@ is closed.
//  --
//  -- In other words, both the @Source@ and @Sink@ will always be closed. If you
//  -- would like to keep the @Source@ open to be used for another operations, use
//  -- the connect-and-resume operators '$$+'.
//  --
  def =%=[F[_], A, B, C, R](p1: Pipe[A, B, F, Unit], p2: Pipe[B, C, F, R])(implicit M: Monad[F]): Pipe[A, C, F, R] = pipe(p1, p2)

}

object Conduits extends ConduitsFunctions {
  class SourceW[F[_], A](src: Source[F, A]) {
    /**
     * The connect operator which pulls data from the source and pushed to a sink.
     * This process can terminate in two ways:
     *
     * <ol>
     *   <li> If the Sink is a `Done` constructor the Source is closed.
     *   <li> If the Source is a `Done` constructor the Sink is closed.
     * </ol>
     *
     * The above means that both Source and Sink will always be closed.
     */
    def %%==[B](sink: Sink[A, F, B])(implicit M: Monad[F]): F[B] = runPipe(pipe(src, sink))

    def %=[B](conduit: Conduit[A, F, B])(implicit M: Monad[F]): Source[F, B] = pipe(src, conduit)

    def zip[B](that: Source[F, B])(implicit M: Monad[F]): Source[F, (A, B)] = CL.zip(src, that)
  }

  class ConduitW[F[_], A, B](c: Conduit[A, F, B]) {
    def =%[C](s: Sink[B, F, C])(implicit M: Monad[F]): Sink[A, F, C] = pipe(c, s)

    def %=(s: Source[F, A])(implicit M: Monad[F]): Source[F, B] = pipe(s, c)
  }

  implicit def pToSource[F[_], A](s: Source[F, A]) = new SourceW(s)
  implicit def pToConduit[F[_], A, B](c: Conduit[A, F, B]) = new ConduitW(c)
}

