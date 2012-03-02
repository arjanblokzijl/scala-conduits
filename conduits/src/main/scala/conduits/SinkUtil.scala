package conduits

import scalaz.Monad
import scalaz.effect.IO

/**
* User: arjan
*/
trait SinkStateResult[S, I, A]
case class StateDone[S, I, A](maybeInput: Option[I], output: A) extends SinkStateResult[S, I, A]
case class StateProcessing[S, I, A](state: S) extends SinkStateResult[S, I, A]

//data SinkIOResult input output = IODone (Maybe input) output | IOProcessing
trait SinkIOResult[A, B]
case class IODone[A, B](maybeInput: Option[A], output: B) extends SinkIOResult[A, B]
case class IOProcessing[A, B]() extends SinkIOResult[A, B]

object SinkUtil {

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
    def push1(state1: S)(input: I): F[SinkResult[I, F, A]] = {
      M.bind(push(state1)(input))((res: SinkStateResult[S, I, A]) => res match {
        case StateProcessing(state1) => M.point(Processing(push1(state1), close(state1)))
        case StateDone(mleftover, output) => M.point(Done(mleftover, output))
      })
    }

    SinkData(push1(state), close(state))
  }

  def sinkIO[F[_], A, B, S](alloc: IO[S], cleanup: S => IO[Unit], push: S => A => F[SinkIOResult[A, B]], close: S => F[B])(implicit M0: MonadResource[F]): Sink[A, F, B] = {
    implicit val M = M0.MO
    def push1(key: ReleaseKey)(state: S)(input: A): F[SinkResult[A, F, B]] = {
      M.bind(push(state)(input))(res => res match {
        case IODone(a, b) => M.bind(M0.release(key))(_ => M.point(Done(a, b)))
        case IOProcessing() => M.point(Processing(push1(key)(state), close1(key)(state)))
      })
    }
    def close1(key: ReleaseKey)(state: S): F[B] = {
      M.bind(close(state))(res => M.bind(M0.release(key))(_ => M.point(res)))
    }
    SinkData(sinkPush = input =>
                  M.bind(M0.allocate(alloc, cleanup))((a) => push1(a._1)(a._2)(input)),
             sinkClose = M.bind(M0.allocate(alloc, cleanup))((a) => close1(a._1)(a._2)))
  }
}
