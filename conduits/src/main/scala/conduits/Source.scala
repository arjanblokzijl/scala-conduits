package conduits

import resource._
import scalaz.{Monoid, Functor, Monad}
import sinks._

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
  def map[B](f: (A) => B)(implicit M: Monad[F]): Source[F, B] = {
    val p = this.sourcePull
    val c = this.sourceClose
    Source(resourceTMonad[F].map[SourceResult[F, A], SourceResult[F, B]](p)(r => r.map(f)), c)
  }

  def >>== [B](sink: Sink[A, F, B])(implicit R: Resource[F]): ResourceT[F, B] = Conduits.normalConnect(this, sink)
}

trait SourceInstances {
  implicit def sourceFunctor[F[_]](implicit M0: Monad[F]): Functor[({type l[a] = Source[F, a]})#l] = new Functor[({type l[a] = Source[F, a]})#l] {
     def map[A, B](fa: Source[F, A])(f: (A) => B): Source[F, B] = fa map f
  }

  implicit def sourceResultFunctor[F[_]](implicit M0: Monad[F]): Functor[({type l[a] = SourceResult[F, a]})#l] = new Functor[({type l[a] = SourceResult[F, a]})#l] {
     def map[A, B](fa: SourceResult[F, A])(f: (A) => B): SourceResult[F, B] = fa map f
  }

  implicit def sourceMonoid[A, F[_]](implicit R0: Resource[F]): Monoid[Source[F, A]] = new SourceMonoid[A, F] {
    implicit val M = R0.F
  }
}

private[conduits] trait SourceMonoid[A, F[_]] extends Monoid[Source[F, A]] {
  implicit val M: Monad[F]
  val rtm = resourceTMonad[F]

  def append(f1: Source[F, A], f2: => Source[F, A]): Source[F, A] = mconcat(Stream(f1, f2))

  private def mconcat(s: Stream[Source[F, A]]): Source[F, A] = {
    def src(next: Source[F, A], rest: Stream[Source[F, A]]): Source[F, A] = Source(pull(next, rest), close(next, rest))
    def pull(current: Source[F, A], rest: Stream[Source[F, A]]): ResourceT[F, SourceResult[F, A]] = {
      rtm.bind(current.sourcePull)(res => res match {
        case Closed() => rest match {
          case a #:: as => pull(a, as)
          case Stream.Empty => rtm.point(Closed[F, A]())
        }
      })
    }
    def close(current: Source[F, A], rest: Stream[Source[F, A]]): ResourceT[F, Unit] = current.sourceClose

    s match {
      case Stream.Empty => zero
      case next0 #::rest0 => src(next0, rest0)
    }
  }


  def zero = Source(sourcePull = rtm.point(Closed[F, A]()), sourceClose = rtm.point(()))
}

object source extends SourceInstances

