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
case class Producing[I, F[_], A](conduit: Conduit[I, F, A], output: Stream[A]) extends ConduitResult[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Producing(conduit map f, output.map(f))
}
case class Finished[I, F[_], A](maybeInput: Option[I], output: Stream[A])  extends ConduitResult[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Finished(maybeInput, output.map(f))
}

trait ConduitFunctions {
  type ConduitPush[I, F[_], A] = I => F[ConduitResult[I, F, A]]
  type ConduitClose[F[_], A] = F[Stream[A]]
}

trait ConduitInstances {
  implicit def conduitFunctor[I, F[_]](implicit M0: Monad[F]): Functor[({type l[a] = Conduit[I, F, a]})#l] = new Functor[({type l[a] = Conduit[I, F, a]})#l] {
     def map[A, B](fa: Conduit[I, F, A])(f: (A) => B): Conduit[I, F, B] = fa map f
  }

  implicit def conduitResultFunctor[I, F[_]](implicit M0: Monad[F]): Functor[({type l[a] = ConduitResult[I, F, a]})#l] = new Functor[({type l[a] = ConduitResult[I, F, a]})#l] {
     def map[A, B](fa: ConduitResult[I, F, A])(f: (A) => B): ConduitResult[I, F, B] = fa map f
  }
}

//private[conduits] trait ConduitFunctor[I, F[_]] extends Functor[({type l[a] = Conduit[I, F, a]})#l] {
//  implicit def M: Monad[F]
//  def map[A, B](fa: Conduit[I, F, A])(f: (A) => B): Conduit[I, F, B] = M.map(fa)((a: A) => {
//    Conduit[I, F, B]((i: I) => resourceTMonad[F].map[ConduitResult[I, F, A], ConduitResult[I, F, B]](fa.conduitPush(i))((r: ConduitResult[I, F, A]) => r.map[B](a)),
//      resourceTMonad[F].map[A, B](fa.conduitClose)(r => f(r)))
//  })
//}

object conduits extends ConduitFunctions with ConduitInstances