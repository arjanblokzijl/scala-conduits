package conduits

/**
* User: arjan
*/

import conduits._
import scalaz.{Functor, Monad}

case class Conduit[I, F[_], A](conduitPush: ConduitPush[I, F, A],
                               conduitClose: ConduitClose[F, A]) {
  def map[B](f: (A) => B)(implicit M: Monad[F]): Conduit[I, F, B] = {
    val c = conduitClose
    Conduit[I, F, B]((i: I) => M.map[ConduitResult[I, F, A], ConduitResult[I, F, B]](conduitPush(i))((r: ConduitResult[I, F, A]) => r.map[B](f)),
      M.map[Stream[A], Stream[B]](c)(r => r.map(f)))
  }
}

sealed trait ConduitResult[I, F[_], A] {
  def map[B](f: A => B)(implicit M: Monad[F]): ConduitResult[I, F, B]
}

case class Running[I, F[_], A](push: ConduitPush[I, F, A], close: F[A]) extends ConduitResult[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Running(i => M.map(push(i))(p => p map f), M.map(close)(f))
}
case class Finished[I, F[_], A](maybeInput: Option[I], output: Stream[A])  extends ConduitResult[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Finished(maybeInput, output.map(f))
}
case class HaveMore[I, F[_], A](pull: ConduitPull[I, F, A], close: F[Unit], output: A)  extends ConduitResult[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = HaveMore(M.map(pull)(a => a.map(f)), close, f(output))
}

trait ConduitFunctions {
}

trait ConduitInstances {
  implicit def conduitFunctor[I, F[_]](implicit M0: Monad[F]): Functor[({type l[a] = Conduit[I, F, a]})#l] = new Functor[({type l[a] = Conduit[I, F, a]})#l] {
     def map[A, B](fa: Conduit[I, F, A])(f: (A) => B): Conduit[I, F, B] = fa map f
  }

  implicit def conduitResultFunctor[I, F[_]](implicit M0: Monad[F]): Functor[({type l[a] = ConduitResult[I, F, a]})#l] = new Functor[({type l[a] = ConduitResult[I, F, a]})#l] {
     def map[A, B](fa: ConduitResult[I, F, A])(f: (A) => B): ConduitResult[I, F, B] = fa map f
  }
}


object conduits extends ConduitFunctions with ConduitInstances {
  type ConduitPush[I, F[_], A] = I => F[ConduitResult[I, F, A]]
  type ConduitClose[F[_], A] = F[Stream[A]]
  type ConduitPull[I, F[_], A] = F[ConduitResult[I, F, A]]
}