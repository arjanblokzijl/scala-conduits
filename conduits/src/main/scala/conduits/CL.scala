package conduits

import resourcet.resource
import scalaz.{Monoid, Monad}
import scalaz.std.stream._

/**
*
* List like operations for conduits.
*/
object CL {
  import SinkUtil._
  import SourceUtil._
  import resource._

  /**
   * A strict left fold.
   * @param z the starting value
   * @param f the binary operation applied to each value
   * @param M the [[scalaz.Monad]] representing the effect
   * @tparam F
   * @tparam A
   * @tparam B
   * @return the result of applying the binary operation to all elements of the stream plus the starting value.
   */
  def foldLeft[F[_], A, B](z: => B)(f: (B, A) => B)(implicit M: Monad[F]): Sink[A, F, B] = {
    def push: B => (=> A) => F[SinkStateResult[B, A, B]] = acc => input => M.point(StateProcessing(f(acc, input)))
    def close: B => F[B] = acc => M.point(acc)
    sinkState[B, A, F, B](z, push, close)
  }

  def sumSink[F[_]](implicit M: Monad[F]): Sink[Int, F, Int] = foldLeft((0: Int))(_ + _)

  /**
   * Takes a number of values from the data stream and returns a the elements as a [[scala.collection.immutable.Stream]].
   * @param n the number of elements to return
   * @param M the Monad instance
   * @tparam F the type representing an effect.
   * @tparam A the type of input, as well as output elements.
   * @return
   */
  def take[F[_], A](n: Int)(implicit M: Monad[F]): Sink[A, F, Stream[A]] = {
    type St = (Int, Stream[A] => Stream[A])
    def push: St => (=> A) => F[SinkStateResult[St, A, Stream[A]]] = st => x => {
      val count1 = st._1 - 1
      def front1(str: Stream[A]) = st._2(str)
      M.point(if (count1 == 0) StateDone(None, front1(x #:: Stream.empty[A]))
                else StateProcessing((count1, front1 _)))
    }
    def close: St => F[Stream[A]] = st => M.point(st._2(Stream.empty[A]))
    sinkState[St, A, F, Stream[A]]((n, identity _), push, close)
  }

  def sourceList[F[_], A](l: Stream[A])(implicit M: Monad[F]): Source[F, A] = {
    def go(l1: Stream[A]): F[SourceStateResult[Stream[A], A]] = l1 match {
      case Stream.Empty => M.point(StateClosed())
      case x #:: xs =>  M.point(StateOpen(xs, x))
    }
    sourceState[Stream[A], F, A](l, (s: Stream[A]) => go(s))
  }

//  -- | Apply a transformation to all values in a stream.
//  --
//  -- Since 0.2.0
//  map :: Monad m => (a -> b) -> Conduit a m b
//  map f =
//      Conduit push close
//    where
//      push i = return $ HaveMore (return $ Running push close) (return ()) (f i)
//      close = mempty

//  def map[F[_], A, B](f: A => B)(implicit M: Monad[F]): Conduit[A, F, B] = {
//    def closeI = M.point(Stream.empty[B])
////    def pushI(a: A): conduits.ConduitPush[A, F, B] = {
////      val run: Running[A, F, B] = Running[A, F, B](i => pushI(a)(i), closeI(f(a)))
////      M.point(HaveMore[A, F, B](M.point(run), M.point(), f(a)))
////    }
//    def pushI: conduits.ConduitPush[A, F, B] = a => {
////      M.point(HaveMore(M.point(Running(i => push(i), close(a))), M.point(), f(a)))
////      val run: Running[A, F, B] = Running[A, F, B](i => pushI(a)(i), closeI(f(a)))
//      M.point(HaveMore[A, F, B](M.point(Running[A, F, B](i => pushI(i), closeI)), M.point(), f(a)))
//    }
//    Conduit(pushI, closeI)
//  }
}
