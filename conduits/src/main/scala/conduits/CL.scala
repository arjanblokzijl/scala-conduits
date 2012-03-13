package conduits

import resourcet.resource
import scalaz.{Monoid, Monad}
import scalaz.std.stream._
import Sink._
import Conduit._

/**
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
    def go(count: Int, acc: Stream[A]) = Processing(push(count, acc), M.point(acc))
    def plus(l1: Stream[A])(l2: Stream[A]): Stream[A] = l1 append l2
    def push(count: Int, acc: Stream[A])(x: A): Sink[A, F, Stream[A]] = {
       if (count == 0) Done(Some(x), acc)
       else {
         val count1 = count - 1
         if (count1 == 0) Done(None, plus(acc)(Stream(x)))
         else Processing(push(count1, plus(acc)(Stream(x))), M.point(plus(acc)(Stream(x))))
       }
    }
    go(n, Stream.empty[A])
  }

  def sourceList[F[_], A](l: => Stream[A])(implicit M: Monad[F]): Source[F, A] = {
    def go(l1: => Stream[A]): F[SourceStateResult[Stream[A], A]] = l1 match {
      case Stream.Empty => M.point(StateClosed.apply)
      case x #:: xs =>  M.point(StateOpen(xs, x))
    }
    sourceState[Stream[A], F, A](l, (s: Stream[A]) => go(s))
  }

  def map[F[_], A, B](f: A => B)(implicit M: Monad[F]): Conduit[A, F, B] = {
    def close = source.sourceMonoid[B, F].zero
    def push: conduits.ConduitPush[A, F, B] = i =>
      HaveMore[A, F, B](Running[A, F, B](push, close), M.point(()), f(i))

    Running(push, close)
  }
}
