package conduits

import scalaz.{Monoid, Functor, Monad}
import sinks._

/**
* User: arjan
*/
sealed trait Source[F[_], A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]): Source[F, B] //TODO move implementation here

  def %= [B](conduit: Conduit[A, F, B])(implicit M: Monad[F]): Source[F, B] = Conduits.normalFuseLeft(this, conduit)
  def >>== [B](sink: Sink[A, F, B])(implicit M: Monad[F]): F[B] = Conduits.normalConnect(this, sink)

  def sourceClose(implicit M: Monad[F]): F[Unit] = this match {
    case Closed() => M.point(())
    case Open(_, close, _) => close
    case SourceM(_, close) => close
  }

  def append(that: Source[F, A])(implicit M: Monad[F]): Source[F, A] = {
    def go(f1: Source[F, A], f2 : Source[F, A]): Source[F, A] = (f1, f2) match {
      case (x, Closed()) => x
      case (Closed(), y) => y
      case (Open(next, close, a), y) => Open[F, A](go(next, y), close, a)
      case (SourceM(msrc, close), y) => SourceM[F, A](M.map(msrc)(src => go(src, y)), close)
    }
    go(this, that)
  }
}

case class Open[F[_], A](source: Source[F, A], close: F[Unit], a: A) extends Source[F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Open[F, B](source.map(f), close, f(a))
}
case class Closed[F[_], A]() extends Source[F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Closed[F, B]()
}
case class SourceM[F[_], A](msrc: F[Source[F, A]], close: F[Unit]) extends Source[F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = SourceM[F, B](M.map(msrc)(s => s.map(f)), close)
}


trait SourceInstances {
  implicit def sourceFunctor[F[_]](implicit M0: Monad[F]): Functor[({type l[a] = Source[F, a]})#l] = new Functor[({type l[a] = Source[F, a]})#l] {
     def map[A, B](fa: Source[F, A])(f: (A) => B): Source[F, B] = fa map f
  }

  implicit def sourceMonoid[A, F[_]](implicit M0: Monad[F]): Monoid[Source[F, A]] = new SourceMonoid[A, F] {
    implicit val M = M0
  }
}

private[conduits] trait SourceMonoid[A, F[_]] extends Monoid[Source[F, A]] {
  implicit val M: Monad[F]

  def append(f1: Source[F, A], f2: => Source[F, A]): Source[F, A] = f1 append f2

  def zero = Closed[F, A]()
}

object source extends SourceInstances

