package conduits

/**
* User: arjan
*/

import conduits._
import scalaz.{Functor, Monad}

sealed trait Conduit[I, F[_], A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]): Conduit[I, F, B]
}

case class Running[I, F[_], A](push: ConduitPush[I, F, A], close: ConduitClose[F, A]) extends Conduit[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Running[I, F, B](i => push(i) map f, close map f)
}
case class Finished[I, F[_], A](maybeInput: Option[I])  extends Conduit[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Finished(maybeInput)
}
case class HaveMore[I, F[_], A](pull: ConduitPull[I, F, A], close: F[Unit], output: A) extends Conduit[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = HaveMore[I, F, B](pull map f, close, f(output))
}
case class ConduitM[I, F[_], A](mcon: F[Conduit[I, F, A]], close: F[Unit]) extends Conduit[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = ConduitM(M.map(mcon)(c => c map f), close)
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