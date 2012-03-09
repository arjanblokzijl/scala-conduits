package conduits

/**
* User: arjan
*/

import conduits._
import scalaz.{Functor, Monad}

sealed trait Conduit[A, F[_], B] {
  def map[C](f: (B) => C)(implicit M: Monad[F]): Conduit[A, F, C]

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

case class Running[A, F[_], B](push: ConduitPush[A, F, B], close: ConduitClose[F, B]) extends Conduit[A, F, B] {
  def map[C](f: (B) => C)(implicit M: Monad[F]) = Running[A, F, C](i => push(i) map f, close map f)
}
case class Finished[A, F[_], B](maybeInput: Option[A])  extends Conduit[A, F, B] {
  def map[C](f: (B) => C)(implicit M: Monad[F]) = Finished(maybeInput)
}
case class HaveMore[A, F[_], B](pull: ConduitPull[A, F, B], close: F[Unit], output: B) extends Conduit[A, F, B] {
  def map[C](f: (B) => C)(implicit M: Monad[F]) = HaveMore[A, F, C](pull map f, close, f(output))
}
case class ConduitM[A, F[_], B](mcon: F[Conduit[A, F, B]], close: F[Unit]) extends Conduit[A, F, B] {
  def map[C](f: (B) => C)(implicit M: Monad[F]) = ConduitM(M.map(mcon)(c => c map f), close)
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