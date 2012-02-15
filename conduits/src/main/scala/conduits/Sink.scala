package conduits

import scalaz.{Functor, Monad}

import sinks._
import resource._

sealed trait Sink[I, F[_], O]
case class SinkNoData[I, F[_], O](output: O) extends Sink[I, F, O]
case class SinkData[I, F[_], O](sinkPush: SinkPush[I, F, O],
                                sinkClose: SinkClose[I, F, O]) extends Sink[I, F, O]

sealed trait SinkResult[I, F[_], O] {
  //pattern matching directly here gives the 'type constructor inapplicable for none' compiler error. This is solved with the latest scala dist.
  //as a temporary workaround, make map abstract and override in the subclasses.
  def map[B](f: O => B)(implicit M: Monad[F]): SinkResult[I, F, B]
  //  def map[B](f: O => B)(implicit M: Monad[F]): SinkResult[I, F, B] = this match {
//      case proc: Processing[I, F, B] =>
//        Processing[I, F, B](push = i =>
//          resourceTMonad[F].map[SinkResult[I, F, O], SinkResult[I, F, B]](proc.push(i))((r: SinkResult[I, F, O]) => r.map(f))
//          , close = resourceTMonad[F].map[SinkResult[I, F, O], SinkResult[I, F, B]](proc.close)((r: SinkResult[I, F, O]) => r.map(f)))
//      case d: Done[I, F, O] => Done[I, F, B](d.input, f(d.output))
//    }
}
case class Processing[I, F[_], O](push: SinkPush[I, F, O], close: SinkClose[I, F, O]) extends SinkResult[I, F, O] {
  def map[B](f: O => B)(implicit M: Monad[F]): SinkResult[I, F, B] = Processing[I, F, B](i =>
    resourceTMonad[F].map[SinkResult[I, F, O], SinkResult[I, F, B]](push(i))((r: SinkResult[I, F, O]) => r.map(f))
    , resourceTMonad[F].map[O, B](close)((r: O) => f(r)))

}

case class Done[I, F[_], O](input: Option[I], output: O) extends SinkResult[I, F, O] {
  def map[B](f: O => B)(implicit M: Monad[F]): SinkResult[I, F, B] = Done[I, F, B](input, f(output))
}

trait SinkInstances {

  implicit def sinkResultFunctor[I, F[_]](implicit M: Monad[F]): Functor[({type l[a] = SinkResult[I, F, a]})#l] = new Functor[({type l[a] = SinkResult[I, F, a]})#l] {
    def map[A, B](fa: SinkResult[I, F, A])(f: (A) => B): SinkResult[I, F, B] = fa match {
      case Processing(p, c) => Processing[I, F, B](push = i =>
        resourceTMonad[F].map[SinkResult[I, F, A], SinkResult[I, F, B]](p(i))((r: SinkResult[I, F, A]) => r.map(f))
        , resourceTMonad[F].map[A, B](c)(r => f(r)))
      case Done(input, output) => Done(input, f(output))
    }
  }

  implicit def sinkFunctor[I, F[_]](implicit M: Monad[F]): Functor[({type l[a] = Sink[I, F, a]})#l] = new Functor[({type l[a] = Sink[I, F, a]})#l] {
    def map[A, B](fa: Sink[I, F, A])(f: (A) => B): Sink[I, F, B] = fa match {
      case SinkNoData(o) => SinkNoData(f(o))
      case SinkData(p, c) => SinkData(sinkPush = i =>
                                                  resourceTMonad[F].map[SinkResult[I, F, A], SinkResult[I, F, B]](p(i))((r: SinkResult[I, F, A]) => r.map(f)),
                                      sinkClose = resourceTMonad[F].map[A, B](c)(r => f(r))
                                      )
    }
  }

  implicit def sinkMonad[I, F[_]](implicit R: Resource[F]): Monad[({type l[a] = Sink[I, F, a]})#l] = new Monad[({type l[a] = Sink[I, F, a]})#l] {
    implicit val M: Monad[F] = R.F
    val rtm = resourceTMonad[F]
    def point[A](a: => A) = SinkNoData(a)

    def bind[A, B](fa: Sink[I, F, A])(f: (A) => Sink[I, F, B]) = {
      def pushHelper(i: Option[I])(r: Sink[I, F, B]): ResourceT[F, SinkResult[I, F, B]] = (i, r) match {
        case (lo, SinkNoData(y)) => rtm.point(Done(lo, y))
        case (Some(l), (SinkData(pushF, _))) => pushF(l)
        case (None, (SinkData(pushF, closeF))) => rtm.point(Processing(pushF, closeF))
      }
      def closeHelper(s: Sink[I, F, B]): ResourceT[F, B] = s match {
        case SinkNoData(y) => rtm.point(y)
        case SinkData(_, closeF) => closeF
      }
      def close(closei: SinkClose[I, F, A]): SinkClose[I, F, B] = rtm.bind(closei)((output: A) => closeHelper(f(output)))

      def push(pushi : SinkPush[I, F, A])(i: I): ResourceT[F, SinkResult[I, F, B]] = {
        rtm.bind(pushi(i))((res: SinkResult[I, F, A]) => res match {
          case Done(lo, output) => pushHelper(lo)(f(output))
          case Processing(pushii, closeii) => rtm.point(Processing(push(pushii), (close(closeii))))
        })
      }

      fa match {
        case SinkNoData(x) => f(x)
        case SinkData(push0, close0) => SinkData(push(push0), close(close0))

      }
    }
  }
}

trait SinkFunctions {
  type SinkPush[I, F[_], O] = I => ResourceT[F, SinkResult[I, F, O]]
  type SinkClose[I, F[_], O] = ResourceT[F, O]
}

object sinks extends SinkFunctions with SinkInstances