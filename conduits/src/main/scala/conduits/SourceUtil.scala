package conduits

import scalaz.Monad

sealed trait SourceStateResult[S, A]
private case class StateOpen[S, A](state: S, output: A) extends SourceStateResult[S, A]
private case class StateClosed[S, A]() extends SourceStateResult[S, A]

object SourceUtil {

  def sourceState[S, F[_], A](state: => S, pull: S => F[SourceStateResult[S, A]])(implicit M: Monad[F]): Source[F, A] = {
    def pull1(state: S): F[Source[F, A]] = {
      M.bind(pull(state))(res => res match {
        case StateOpen(state1, o) => M.point(Open(src(state1), close, o))
        case StateClosed() => M.point(Closed())
      })
    }
    def close = M.point(())
    def src(state1: S) = SourceM(pull1(state1), close)

    src(state)
  }
}