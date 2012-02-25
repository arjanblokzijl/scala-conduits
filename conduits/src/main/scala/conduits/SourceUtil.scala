package conduits

import scalaz.Monad

sealed trait SourceStateResult[S, A]
private case class StateOpen[S, A](state: S, output: A) extends SourceStateResult[S, A]
private case class StateClosed[S, A]() extends SourceStateResult[S, A]

object SourceUtil {
  import resource._

  def sourceState[S, F[_], A](state: => S, pull: S => ResourceT[F, SourceStateResult[S, A]])(implicit M: Monad[F]): Source[F, A] = {
    val rtm = resourceTMonad[F]

    def pull1(state: S): ResourceT[F, SourceResult[F, A]] = {
      rtm.bind(pull(state))(res => res match {
        case StateOpen(state1, o) => rtm.point(Open(src(state1), o))
        case StateClosed() => rtm.point(Closed())
      })
    }
    def close = rtm.point(())
    def src(state1: S) = Source(pull1(state1), close)

    src(state)
  }
}