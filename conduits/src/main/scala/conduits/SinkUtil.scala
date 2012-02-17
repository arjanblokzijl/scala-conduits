package conduits

import scalaz.Monad

/**
 * User: arjan
 */
trait SinkStateResult[S, I, A]
case class StateDone[S, I, A](maybeInput: Option[I], output: A) extends SinkStateResult[S, I, A]
case class StateProcessing[S, I, A](state: S) extends SinkStateResult[S, I, A]

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
  def sinkState[S, I, F[_], A](state: => S, push: S => (=> I) => ResourceT[F, SinkStateResult[S, I, A]], close: S => ResourceT[F, A])(implicit R: Resource[F]): Sink[I, F, A] = {
    implicit val M = R.F
    val rtm = resourceTMonad[F]
    def push1(state1: S)(input: I): ResourceT[F, SinkResult[I, F, A]] = {
      rtm.bind(push(state1)(input))((res: SinkStateResult[S, I, A]) => res match {
        case StateProcessing(state1) => rtm.point(Processing(push1(state1), close(state1)))
        case StateDone(mleftover, output) => rtm.point(Done(mleftover, output))
      })
    }

    SinkData(push1(state), close(state))
  }
}
