package conduits

/**
 *
 * List like operations for conduits.
 */
object CL {
  import SinkUtil._
  import resource._

  /**
   * A strict left fold.
   * @param z the starting value
   * @param f the binary operation applied to each value
   * @param R the [[conduits.Resource]]
   * @tparam F
   * @tparam A
   * @tparam B
   * @return the result of applying the binary operation to all elements of the stream plus the starting value.
   */
  def foldLeft[F[_], A, B](z: => B)(f: (B, A) => B)(implicit R: Resource[F]): Sink[A, F, B] = {
    implicit val M = R.F
    implicit val rtm = resourceTMonad[F]
    def push: B => (=> A) => ResourceT[F, SinkStateResult[B, A, B]] = acc => input => rtm.point(StateProcessing(f(acc, input)))
    def close: B => ResourceT[F, B] = acc => rtm.point(acc)
    sinkState[B, A, F, B](z, push, close)
  }

  def sumSink[F[_]](implicit R: Resource[F]): Sink[Int, F, Int] = foldLeft((0: Int))(_ + _)

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
