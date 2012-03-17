package conduits

import scalaz.Monad
import Conduit._
import Source._

/**
 * User: arjan
 */
trait ConduitStateResult[S, A, B] {
  def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> S, => Stream[B]) => Z): Z
}

object ConduitUtil {
  import Folds._
  object StateFinished {
    def apply[S, A, B](maybeInput: => Option[A], output: => Stream[B]) = new ConduitStateResult[S, A, B] {
      def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> S, => Stream[B]) => Z) = finished(maybeInput, output)
    }
    def unapply[S, A, B](r: ConduitStateResult[S, A, B]): Option[(Option[A], Stream[B])] = r.fold((i, o) => Some(i, o), ToNone2)
  }
  object StateProducing {
    def apply[S, A, B](state: => S, output: => Stream[B]) = new ConduitStateResult[S, A, B] {
      def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> S, => Stream[B]) => Z) = producing(state, output)
    }
    def unapply[S, A, B](r: ConduitStateResult[S, A, B]): Option[(S, Stream[B])] = r.fold(ToNone2, (s, o) => Some(s, o))
  }

  /**
   * Construct a 'Conduit' with some stateful functions. This function addresses
   * threading the state value for you.
   */
  def conduitState[S, A, F[_], B](state: => S, push: S => (=> A) => F[ConduitStateResult[S, A, B]], close: S => F[Stream[B]])(implicit M: Monad[F]): Conduit[A, F, B] = {
    def push1(state1: S)(input: A): Conduit[A, F, B] = ConduitM(
      M.map(push(state)(input))(r => goRes(r))
      , M.point(()))

    def close1(state: S): Source[F, B] = SourceM(M.bind(close(state))(os => M.point(fromList(os))), M.point(()))

    def goRes(res: ConduitStateResult[S, A, B]): Conduit[A, F, B] = res.fold(
      finished = (leftover, output) => haveMore[A, F, B](Finished(leftover), M.point(()), output)
     , producing = (state, output) => haveMore[A, F, B](Running[A, F, B](push1(state), close1(state)), M.point(()), output)
    )
    Running(push1(state), close1(state))
  }

  def haveMore[A, F[_], B](res: Conduit[A, F, B], close: F[Unit], bs: Stream[B])(implicit M: Monad[F]): Conduit[A, F, B] = bs match {
    case Stream.Empty => res
    case x #:: xs => HaveMore[A, F, B](haveMore(res, close, xs), close, x)
  }

  def fromList[F[_], A](as: Stream[A])(implicit M: Monad[F]): Source[F, A] = as match {
    case Stream.Empty => Closed.apply[F, A]
    case x #:: xs => Open(fromList(xs), M.point(()), x)
  }
}
