package conduits

import scalaz.Monad

sealed trait SourceStateResult[S, A] {
  def fold[Z](open: (=> StateOpen[S, A]) => Z, closed: => Z): Z
}
private case class StateOpen[S, A](state: S, output: A) extends SourceStateResult[S, A] {
  def fold[Z](open: (=> StateOpen[S, A]) => Z, closed: => Z) = open(StateOpen(state, output))
}
private case class StateClosed[S, A]() extends SourceStateResult[S, A] {
  def fold[Z](open: (=> StateOpen[S, A]) => Z, closed: => Z) = closed
}

object SourceUtil {

  def sourceState[S, F[_], A](state: => S, pull: S => F[SourceStateResult[S, A]])(implicit M: Monad[F]): Source[F, A] = {
    def pull1(state: => S): F[Source[F, A]] = {
      M.bind(pull(state))(res => res.fold(open = o => M.point(Open(src(o.state), close, o.output)), closed = M.point(Closed())))
    }
    def close = M.point(())
    def src(state1: => S) = SourceM(pull1(state1), close)
    src(state)
  }
}