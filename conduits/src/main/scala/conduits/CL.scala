package conduits


/**
 *
 * List like operations for conduits.
 */
object CL {
  import SinkUtil._
  import resource._

  /**
   * Takes a number of values from the data stream and returns a the elements as a [[scala.collection.immutable.Stream]].
   * @param n the number of elements to return
   * @param R the Resource instance
   * @tparam F the type representing an effect.
   * @tparam A the type of input, as well as output elements.
   * @return
   */
  def take[F[_], A](n: Int)(implicit R: Resource[F]): Sink[A, F, Stream[A]] = {
    implicit val M = R.F
    implicit val rtm = resourceTMonad[F]
    type St = (Int, Stream[A] => Stream[A])
    def push: St => (=> A) => ResourceT[F, SinkStateResult[St, A, Stream[A]]] = st => x => {
      val count1 = st._1 - 1
      def front1(str: Stream[A]) = st._2(str)
      rtm.point(if (count1 == 0) StateDone(None, front1(x #:: Stream.empty[A]))
                else StateProcessing((count1, front1 _)))
    }
    def close: St => ResourceT[F, Stream[A]] = st => rtm.point(st._2(Stream.empty[A]))
    sinkState[St, A, F, Stream[A]]((n, identity _), push, close)
  }
}
