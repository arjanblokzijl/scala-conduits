package conduits

/**
* User: arjan
*/

import conduits._
import scalaz.{Functor, Monad}
import Sink._
import Conduit._

sealed trait Conduit[A, F[_], B] {
  def fold[Z](running: (=> ConduitPush[A, F, B], => ConduitClose[F, B]) => Z
              , finished: (=> Option[A]) => Z
              , haveMore: (=> ConduitPull[A, F, B], => F[Unit], => B) => Z
              , conduitM: (=> F[Conduit[A, F, B]], => F[Unit]) => Z): Z

  def map[C](f: (B) => C)(implicit M: Monad[F]): Conduit[A, F, C] = fold(
    running = (push, close) => Running[A, F, C](i => push.apply(i) map f, close map f)
    , finished = i => Finished(i)
    , haveMore = (pull, close, output) => HaveMore[A, F, C](pull map f, close, f(output))
    , conduitM = (mcon, close) => ConduitM(M.map(mcon)(c => c map f), close)
  )

  /**Right fuse, combining a conduit and a sink together into a new sink.*/
  def =%[C](sink: Sink[B, F, C])(implicit M: Monad[F]): Sink[A, F, C] = (this, sink) match {
    case (c, SinkM(msink)) => SinkM(M.map(msink)(s => c =% s))
    case (c, Done(leftover, output)) => SinkM(M.map(conduitClose)(_ => Done(None, output)))
    case (Running(push0, close), s) => Processing[A, F, C](input => push0(input) =% s, close >>== s)
    case (Finished(mleftover), Processing(_, close)) => SinkM(M.map(close)(o => Done(mleftover, o)))
    case (HaveMore(con, _, input), Processing(pushI, _)) => con =% pushI(input)
  }

  def conduitClose(implicit M: Monad[F]): F[Unit] = this match {
    case Running(_, c) => c sourceClose
    case Finished(_) => M.point(())
    case HaveMore(_, c, _) => c
    case ConduitM(_, c) => c
  }
}
object Conduit {
  import Folds._
  object Running {
    def apply[A, F[_], B](push: => ConduitPush[A, F, B], close: => ConduitClose[F, B]) = new Conduit[A, F, B] {
      def fold[Z](running: (=> ConduitPush[A, F, B], => ConduitClose[F, B]) => Z
                    , finished: (=> Option[A]) => Z
                    , haveMore: (=> ConduitPull[A, F, B], => F[Unit], => B) => Z
                    , conduitM: (=> F[Conduit[A, F, B]], => F[Unit]) => Z) = running(push, close)
    }
    def unapply[A, F[_], B](conduit: Conduit[A, F, B]): Option[(ConduitPush[A, F, B],  ConduitClose[F, B])] = {
      conduit fold((push, close) => Some(push, close), ToNone1, ToNone3, ToNone2)
    }
  }
  object Finished {
    def apply[A, F[_], B](maybeInput: => Option[A]) = new Conduit[A, F, B] {
      def fold[Z](running: (=> ConduitPush[A, F, B], => ConduitClose[F, B]) => Z
                    , finished: (=> Option[A]) => Z
                    , haveMore: (=> ConduitPull[A, F, B], => F[Unit], => B) => Z
                    , conduitM: (=> F[Conduit[A, F, B]], => F[Unit]) => Z) = finished(maybeInput)
    }
    def unapply[A, F[_], B](conduit: Conduit[A, F, B]): Option[Option[A]] = {
      conduit fold(ToNone2, Some(_), ToNone3, ToNone2)
    }
  }
  object HaveMore {
    def apply[A, F[_], B](pull: => ConduitPull[A, F, B], close: => F[Unit], output: => B) = new Conduit[A, F, B] {
      def fold[Z](running: (=> ConduitPush[A, F, B], => ConduitClose[F, B]) => Z
                    , finished: (=> Option[A]) => Z
                    , haveMore: (=> ConduitPull[A, F, B], => F[Unit], => B) => Z
                    , conduitM: (=> F[Conduit[A, F, B]], => F[Unit]) => Z) = haveMore(pull, close, output)
    }
    def unapply[A, F[_], B](conduit: Conduit[A, F, B]): Option[(ConduitPull[A, F, B], F[Unit], B)] = {
      conduit fold(ToNone2, ToNone1, (p, c, o) => Some(p, c, o), ToNone2)
    }
  }
  object ConduitM {
    def apply[A, F[_], B](mcon: => F[Conduit[A, F, B]], close: => F[Unit]) = new Conduit[A, F, B] {
      def fold[Z](running: (=> ConduitPush[A, F, B], => ConduitClose[F, B]) => Z
                    , finished: (=> Option[A]) => Z
                    , haveMore: (=> ConduitPull[A, F, B], => F[Unit], => B) => Z
                    , conduitM: (=> F[Conduit[A, F, B]], => F[Unit]) => Z) = conduitM(mcon, close)
    }
    def unapply[A, F[_], B](conduit: Conduit[A, F, B]): Option[(F[Conduit[A, F, B]], F[Unit])] = {
      conduit fold(ToNone2, ToNone1, ToNone3, (mcon, close) => Some(mcon, close))
    }
  }
}


trait ConduitFunctions {
}

trait ConduitInstances {
  implicit def conduitFunctor[I, F[_]](implicit M0: Monad[F]): Functor[({type l[a] = Conduit[I, F, a]})#l] = new Functor[({type l[a] = Conduit[I, F, a]})#l] {
     def map[A, B](fa: Conduit[I, F, A])(f: (A) => B): Conduit[I, F, B] = fa map f
  }
}


object conduits extends ConduitFunctions with ConduitInstances {
  type ConduitPush[I, F[_], A] = I => Conduit[I, F, A]
  type ConduitClose[F[_], A] = Source[F, A]
  type ConduitPull[I, F[_], A] = Conduit[I, F, A]
}