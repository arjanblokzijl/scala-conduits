package conduits

import resource._
import scalaz.{Functor, Monad}

/**
 * User: arjan
 */

sealed trait SourceResult[F[_], A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]): SourceResult[F, B]
}

case class Open[F[_], A](source: Source[F, A], a: A) extends SourceResult[F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Open(source.map(f), f(a))
}
case class Closed[F[_], A]() extends SourceResult[F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = Closed[F, B]()
}

case class Source[F[_], A](sourcePull: ResourceT[F, SourceResult[F, A]],
                           sourceClose: ResourceT[F, Unit]) {
//  fmap f src = src
//          { sourcePull = liftM (fmap f) (sourcePull src)
//          }
  def map[B](f: (A) => B)(implicit M: Monad[F]): Source[F, B] = {
    val p = this.sourcePull
    val c = this.sourceClose
    Source(resourceTMonad[F].map[SourceResult[F, A], SourceResult[F, B]](p)(r => r.map(f)), c)
  }
}

trait SourceInstances {
  implicit def sourceFunctor[F[_]](implicit M0: Monad[F]): Functor[({type l[a] = Source[F, a]})#l] = new Functor[({type l[a] = Source[F, a]})#l] {
     def map[A, B](fa: Source[F, A])(f: (A) => B): Source[F, B] = fa map f
  }

  implicit def sourceResultFunctor[F[_]](implicit M0: Monad[F]): Functor[({type l[a] = SourceResult[F, a]})#l] = new Functor[({type l[a] = SourceResult[F, a]})#l] {
     def map[A, B](fa: SourceResult[F, A])(f: (A) => B): SourceResult[F, B] = fa map f
  }

}

object source extends SourceInstances

