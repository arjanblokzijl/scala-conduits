package conduits

import scalaz.effect.IO
import resourcet.{ReleaseKey, MonadResource, resource}
import scalaz.{Forall, Monad}
import Sink._

/**
* User: arjan
*/
trait SinkStateResult[S, I, A] {
  def fold[Z](done: (=> Option[I], => A) => Z, processing: (=> S) => Z): Z
}

trait SinkIOResult[A, B] {
  def fold[Z](done: (=> Option[A], => B) => Z, processing: => Z): Z
}

object SinkUtil {
  import Folds._
  object StateDone {
    def apply[S, I, A](maybeInput: => Option[I], output: => A) = new SinkStateResult[S, I, A] {
      def fold[Z](done: (=> Option[I], => A) => Z, processing: (=> S) => Z) = done(maybeInput, output)
    }
    def unapply[S, I, A](r: SinkStateResult[S, I, A]): Option[(Option[I], A)] = r.fold((i, a) => Some(i, a), ToNone)
  }

  object StateProcessing {
    def apply[S, I, A](s: => S) = new SinkStateResult[S, I, A] {
      def fold[Z](done: (=> Option[I], => A) => Z, processing: (=> S) => Z) = processing(s)
    }
    def unapply[S, I, A](r: SinkStateResult[S, I, A]): Option[S] = r.fold(ToNone2, s => Some(s))
  }

  object IODone {
    def apply[A, B](maybeInput: => Option[A], output: => B) = new SinkIOResult[A, B] {
      def fold[Z](done: (=> Option[A], => B) => Z, processing: => Z) = done(maybeInput, output)
    }
    def unapply[A, B](r: SinkIOResult[A, B]): Option[(Option[A], B)] = r.fold((a, b) => Some(a, b), None)
  }

  object IOProcessing {
    def apply[A, B] = new SinkIOResult[A, B] {
      def fold[Z](done: (=> Option[A], => B) => Z, processing: => Z) = processing
    }
    def unapply[A, B](r: SinkIOResult[A, B]): Boolean = r.fold((_,_) => false, true)
  }

  import resource._

  /**
   * Construct a 'Sink' with some stateful functions. This function addresses
   * threading the state value for you.
   * @param state the initial state
   * @param push Function that pushes data into the sake, threading the state value.
   * @param close takes the current state and returns an output
   * @tparam S the type of the State
   * @tparam I the Input element type
   * @tparam F the type representing an effect
   * @tparam A the type of the calculated result.
   * @return
   */
  def sinkState[S, I, F[_], A](state: => S, push: S => (=> I) => F[SinkStateResult[S, I, A]], close: S => F[A])(implicit M: Monad[F]): Sink[I, F, A] = {
    def push1(state1: S)(input: I): Sink[I, F, A] = SinkM(
      M.bind(push(state1)(input))((res: SinkStateResult[S, I, A]) =>
         res.fold(done = (i, a) => M.point(Done(i, a)),
         processing = s => M.point(Processing(push1(s), close(s))))))

    Processing(push1(state), close(state))
  }

  def sinkIO[F[_], A, B, S](alloc: IO[S], cleanup: S => IO[Unit], push: S => A => F[SinkIOResult[A, B]], close: S => F[B])(implicit M0: MonadResource[F]): Sink[A, F, B] = {
    implicit val M = M0.MO
    def push1(key: => ReleaseKey)(state: => S)(input: => A): F[Sink[A, F, B]] = {
      M.bind(push(state)(input))(res => res.fold(done = (a, b) => M.bind(M0.release(key))(_ => M.point(Done(a, b))),
                                                 processing = M.point(Processing(i => SinkM(push1(key)(state)(i)), close1(key)(state)))))
    }
    def close1(key: ReleaseKey)(state: S): F[B] = {
      M.bind(close(state))(res => M.bind(M0.release(key))(_ => M.point(res)))
    }
    Processing(push = input =>
              SinkM(M.bind(M0.allocate(alloc, cleanup))(ks => push1(ks._1)(ks._2)(input))),
             close = M.bind(M0.allocate(alloc, cleanup))((a) => close1(a._1)(a._2)))
  }

  def transSink[F[_], G[_], A, B](f: Forall[({type λ[A] = F[A] => G[A]})#λ], sink: Sink[A, F, B])(implicit M: Monad[F], N: Monad[G]): Sink[A, G, B] = sink match {
    case Done(a, b) => Done(a, b)
    case Processing(push, close) => Processing[A, G, B](i => transSink(f, push(i)), f.apply(close))
    case SinkM(msink) => SinkM[A, G, B](f.apply(M.map(msink)(s => transSink(f, s))))
  }
}
