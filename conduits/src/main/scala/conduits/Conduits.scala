package conduits

/**
* User: arjan
*/
import scalaz.Monad
import pipes._
import Pipe._
import collection.immutable.Stream
import scalaz.effect.MonadControlIO
import resourcet.MonadActive

object Conduits {

  //TODO is there a way to do this without the implicits?
  class SourceOps[F[_], A](src: Source[F, A]) {
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

    /*Alias for `%%==`*/
    def &=[B](sink: Sink[A, F, B])(implicit M: Monad[F]): F[B] = %%==(sink)

    /**
     * The connect and resume operator. This does not close the source, but instead
     * returns it so that it can be used again.
     */
    def %%==+[B](sink: Sink[A, F, B])(implicit M: Monad[F]): F[(Source[F, A], B)] = runPipe(pipeResume(src, sink))

    /**
     * Left fuse, combining a source and a Conduit into a new Source.
     * Both the Source and the Conduit will be closed when the Source is closed.
     */
    def %=[B](conduit: Conduit[A, F, B])(implicit M: Monad[F]): Source[F, B] = pipe(src, conduit)

    /**Zips the content of two Sources.*/
    def zip[B](that: Source[F, B])(implicit M: Monad[F]): Source[F, (A, B)] = CL.zip(src, that)

    /**Lazily consumes the contents of this source.*/
    def lazyConsume(implicit M: MonadControlIO[F], MA: MonadActive[F]): F[Stream[A]] = Lazy.lazyConsume(src)
  }

  class ConduitOps[F[_], A, B](c: Conduit[A, F, B]) {
    /**
     * Right fuse, combining a Conduit and a Sink together into a new Sink.
     */
    def =%[C](s: Sink[B, F, C])(implicit M: Monad[F]): Sink[A, F, C] = pipe(c, s)

    /**
     * Left fuse, combining a source and a Conduit into a new Source.
     * Both the Source and the Conduit will be closed when the Source is closed.
     */
    def %=(s: Source[F, A])(implicit M: Monad[F]): Source[F, B] = pipe(s, c)

    /**
     * Middle fuse, combining a Conduit and another Pipe together into a new Pipe.
     * Both Pipes will be closed when the newly created Conduit is closed.
     */
    def =%=[C, R](p2: Pipe[B, C, F, R])(implicit M: Monad[F]): Pipe[A, C, F, R] = pipe(c, p2)
  }

  implicit def ToSourceOps[F[_], A](s: Source[F, A]) = new SourceOps(s)
  implicit def ToConduitOps[F[_], A, B](c: Conduit[A, F, B]) = new ConduitOps(c)
}
