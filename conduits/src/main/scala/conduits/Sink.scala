package conduits


import sinks._
import resource._
import scalaz.{Applicative, MonadTrans, Functor, Monad}
import scalaz.effect.{IO, MonadIO}

/**
 * A Sink is a consumer of data.
 * Basic examples would be a sum function (adding up a stream of numbers fed in), a file sink (which writes all incoming bytes to a file), or a socket.
 * We push data into a sink. When the sink finishes processing, it returns some value.
 * @tparam I the input element type that the sink consumes
 * @tparam F The type constructor representing an effect.
 * @tparam A The output element type a Sink produces.
 */
sealed trait Sink[I, F[_], A]
case class SinkNoData[I, F[_], A](output: A) extends Sink[I, F, A]
case class SinkData[I, F[_], A](sinkPush: SinkPush[I, F, A],
                                sinkClose: SinkClose[I, F, A]) extends Sink[I, F, A]
case class SinkLift[I, F[_], A](res: ResourceT[F, Sink[I, F, A]]) extends Sink[I, F, A]

sealed trait SinkResult[I, F[_], A] {
  //pattern matching directly here gives the 'type constructor inapplicable for none' compiler error. This is solved with the latest scala dist.
  //as a temporary workaround, make map abstract and override in the subclasses.
  def map[B](f: A => B)(implicit M: Monad[F]): SinkResult[I, F, B]
}

case class Processing[I, F[_], A](push: SinkPush[I, F, A], close: SinkClose[I, F, A]) extends SinkResult[I, F, A] {
  def map[B](f: A => B)(implicit M: Monad[F]): SinkResult[I, F, B] = Processing[I, F, B](i =>
    resourceTMonad[F].map[SinkResult[I, F, A], SinkResult[I, F, B]](push(i))((r: SinkResult[I, F, A]) => r.map(f))
    , resourceTMonad[F].map[A, B](close)((r: A) => f(r)))

}

case class Done[I, F[_], A](input: Option[I], output: A) extends SinkResult[I, F, A] {
  def map[B](f: A => B)(implicit M: Monad[F]): SinkResult[I, F, B] = Done[I, F, B](input, f(output))
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
      case SinkLift(rt) => SinkLift(resourceTMonad[F].map(rt)(r => map(r)(f)))
    }
  }

  implicit def sinkMonad[I, F[_]](implicit R0: Resource[F]): Monad[({type l[a] = Sink[I, F, a]})#l] = new SinkMonad[I, F] {
    implicit val M = R0.F
  }

  implicit def sinkMonadTrans[I, F[_]](implicit R0: Resource[F]): MonadTrans[({type l[a[_], b] = Sink[I, a, b]})#l] = new MonadTrans[({type l[a[_], b] = Sink[I, a, b]})#l] {
    implicit def apply[M[_]](implicit M0: Monad[M]): Monad[({type l[a] = Sink[I, M, a]})#l] = new SinkMonad[I, M] {
      implicit val M = M0
    }

    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): Sink[I, G, A] = {
      import scalaz.Kleisli._
      SinkLift[I, G, A](new ResourceT[G, Sink[I, G, A]] {
        def value[R[_]](implicit D: Dep[G, R]) = kleisli(x => M.map(ga)((a: A) => SinkNoData(a))) //TODO check whether this makes sense
      })
    }
  }

  implicit def sinkMonadIO[I, F[_]](implicit M0: MonadIO[F]): MonadIO[({type l[a] = Sink[I, F, a]})#l] = new SinkMonadIO[I, F] {
    implicit def F = M0
    implicit val M = M0
  }

}

private[conduits] trait SinkMonad[I, F[_]] extends Monad[({type l[a] = Sink[I, F, a]})#l] {
  implicit val M: Monad[F]
  val rtm = resourceTMonad[F]
  def point[A](a: => A) = SinkNoData(a)

  def bind[A, B](fa: Sink[I, F, A])(f: (A) => Sink[I, F, B]) = {
    def pushHelper(i: Option[I])(r: Sink[I, F, B]): ResourceT[F, SinkResult[I, F, B]] = (i, r) match {
      case (lo, SinkNoData(y)) => rtm.point(Done(lo, y))
      case (Some(l), (SinkData(pushF, _))) => pushF(l)
      case (None, (SinkData(pushF, closeF))) => rtm.point(Processing(pushF, closeF))
      case (lo, (SinkLift(msink))) => rtm.bind(msink)(pushHelper(lo))
    }
    def closeHelper(s: Sink[I, F, B]): ResourceT[F, B] = s match {
      case SinkNoData(y) => rtm.point(y)
      case SinkData(_, closeF) => closeF
      case SinkLift(msink) => rtm.bind(msink)(closeHelper(_))
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
      case SinkLift(rt) => SinkLift(rtm.bind(rt)((x: Sink[I, F, A]) => rtm.point(bind(x)(f))))
    }
  }
}

private[conduits] trait SinkMonadIO[I, F[_]] extends MonadIO[({type l[a] = Sink[I, F, a]})#l] with SinkMonad[I, F] {
  implicit def F: MonadIO[F]

  def liftIO[A](ioa: IO[A]) = MonadTrans[({type l[a[_], b] = Sink[I,a, b]})#l].liftM(F.liftIO(ioa))
}

trait SinkFunctions {
  type SinkPush[I, F[_], A] = I => ResourceT[F, SinkResult[I, F, A]]
  type SinkClose[I, F[_], A] = ResourceT[F, A]
}

object sinks extends SinkFunctions with SinkInstances