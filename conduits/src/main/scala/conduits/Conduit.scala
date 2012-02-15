package conduits

/**
 * User: arjan
 */

import conduits._
import scalaz.{Functor, Monad}
import resource._

case class Conduit[I, F[_], O](conduitPush: ConduitPush[I, F, O],
                               conduitClose: ConduitClose[F, O]) {
  def map[B](f: (O) => B)(implicit M: Monad[F]): Conduit[I, F, B] = {
    val c = conduitClose
    Conduit[I, F, B]((i: I) => resourceTMonad[F].map[ConduitResult[I, F, O], ConduitResult[I, F, B]](conduitPush(i))((r: ConduitResult[I, F, O]) => r.map[B](f)),
      resourceTMonad[F].map[Stream[O], Stream[B]](c)(r => r.map(f)))
  }

}

sealed trait ConduitResult[I, F[_], O] {
  def map[B](f: O => B)(implicit M: Monad[F]): ConduitResult[I, F, B]
}
case class Producing[I, F[_], O](c: Conduit[I, F, O], output: Stream[O]) extends ConduitResult[I, F, O] {
  def map[B](f: (O) => B)(implicit M: Monad[F]) = Producing(c map f, output.map(f))
}
case class Finished[I, F[_], O](maybeInput: Option[I], output: Stream[O])  extends ConduitResult[I, F, O] {
  def map[B](f: (O) => B)(implicit M: Monad[F]) = Finished(maybeInput, output.map(f))
}

trait ConduitFunctions {
  type ConduitPush[I, F[_], O] = I => ResourceT[F, ConduitResult[I, F, O]]
  type ConduitClose[F[_], O] = ResourceT[F, Stream[O]]
}

trait ConduitInstances {
  implicit def conduitFunctor[I, F[_]](implicit M0: Monad[F]) = new Functor[({type l[a] = Conduit[I, F, a]})#l] {
     def map[A, B](fa: Conduit[I, F, A])(f: (A) => B): Conduit[I, F, B] = fa map f
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