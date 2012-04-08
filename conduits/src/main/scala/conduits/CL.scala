package conduits

import resourcet.resource
import scalaz.std.stream._
import scalaz._
import collection.immutable.Stream

/**
* List like operations for conduits.
*/
object CL {
  import pipes._
  import Pipe._
  import resource._
  import SinkFunctions._
  import SourceFunctions._
  import ConduitFunctions._

  /**
   * A strict left fold.
   * @param z the starting value
   * @param f the binary operation applied to each value
   * @return the result of applying the binary operation to all elements of the stream plus the starting value.
   */
  def foldLeft[F[_], A, B](z: => B)(f: (B, A) => B)(implicit M: Monad[F]): Sink[A, F, B] = {
    def push: B => (=> A) => F[SinkStateResult[B, A, B]] = acc => input => M.point(StateProcessing(f(acc, input)))
    def close: B => F[B] = acc => M.point(acc)
    sinkState[B, A, F, B](z, push, close)
  }

  def sum[F[_]](implicit M: Monad[F]): Sink[Int, F, Int] = foldLeft((0: Int))(_ + _)

  /**Take a single value from the stream, if available.*/
  def head[F[_], A](implicit M: Monad[F]): Sink[A, F, Option[A]] =
    NeedInput(a =>  Done(None, Some(a)), pipeMonad[A, Zero, F].point[Option[A]](None))

  /**Look at the next value of the stream, if available. This does not alter the content of the stream*/
  def peek[F[_], A](implicit M: Monad[F]): Sink[A, F, Option[A]] =
    NeedInput(a => Done(Some(a), Some(a)), pipeMonad[A, Zero, F].point[Option[A]](None))

  /**
   * Takes a number of values from the data stream and returns a the elements as a [[scala.collection.immutable.Stream]].
   */
  def take[F[_], A](n: Int)(implicit M: Monad[F]): Sink[A, F, Stream[A]] = {
    def app[A](l1: Stream[A], l2: => Stream[A]): Stream[A] = streamInstance.plus(l1, l2)
    def go(count: Int, acc: Stream[A]) = NeedInput(push(count, acc), pipeMonad[A, Zero, F].point(acc))
    def push(count: Int, acc: Stream[A])(x: A): Sink[A, F, Stream[A]] = {
       if (count <= 0) Done(Some(x), acc)
       else {
         val count1 = count - 1
         if (count1 <= 0) Done(None, app(acc, Stream(x)))
         else NeedInput(push(count1, app(acc, Stream(x))), pipeMonad[A, Zero, F].point(app(acc, Stream(x))))
       }
    }
    go(n, Stream.empty[A])
  }

  def drop[F[_], A](count: Int)(implicit M: Monad[F]): Sink[A, F, Unit] = {
    def push(i: A): Sink[A, F, Unit] = (count - 1) match {
      case 0 => Done(None, ())
      case n => drop(n)
    }
    count match {
      case n if (n <= 0) => NeedInput(i => Done(Some(i), ()), pipeMonad[A, Zero, F].point(()))
      case c => NeedInput(push, NeedInput(push, pipeMonad[A, Zero, F].point(())))
    }
  }

  def consume[F[_], A](implicit M: Monad[F]): Sink[A, F, Stream[A]] = {
    def go(acc: Stream[A]): Sink[A, F, Stream[A]] = NeedInput(push(acc), pipeMonad[A, Zero, F].point(acc))
    def push(acc: Stream[A])(x: A): Sink[A, F, Stream[A]] = go(streamInstance.plus(acc, Stream(x)))
    go(Stream.empty[A])
  }

  def filter[F[_], A](f: A => Boolean)(implicit M: Monad[F]): Conduit[A, F, A] = {
    def close = pipeMonoid[A, A, F].zero
    def push: A => Conduit[A, F, A] = i =>
      if (f(i)) HaveOutput(NeedInput(push, close), M.point(()), i)
      else NeedInput(push, close)
    NeedInput(push, close)
  }

  def sourceList[F[_], A](l: => Stream[A])(implicit M: Monad[F]): Source[F, A] = {
    def go(l1: => Stream[A]): F[SourceStateResult[Stream[A], A]] = l1 match {
      case Stream.Empty => M.point(StateClosed.apply)
      case x #:: xs =>  M.point(StateOpen(xs, x))
    }
    sourceState[Stream[A], F, A](l, (s: Stream[A]) => go(s))
  }

  /**
   * Apply a transformation to all values in a stream.
   */
  def map[F[_], A, B](f: A => B)(implicit M: Monad[F]): Conduit[A, F, B] = {
    def close = pipeMonoid[A, B, F].zero
    def push: A => Conduit[A, F, B] = i =>
      HaveOutput(NeedInput(push, close), M.point(()), f(i))

    NeedInput(push, close)
  }

  /**
   * Apply a transformation to all values in a stream, concatenating the output values.
   */
  def concatMap[F[_], A, B](f: A => Stream[B])(implicit M: Monad[F]): Conduit[A, F, B] = {
    def close = pipeMonoid[A, B, F].zero
    def push: A => Conduit[A, F, B] = i =>
      haveMore(NeedInput(push, close), M.point(()), f(i))

    NeedInput(push, close)
  }

  def groupBy[F[_], A](f: (A, A) => Boolean)(implicit M: Monad[F]): Conduit[A, F, Stream[A]] = {
    def push(as: => Stream[A], v: => A): F[ConduitStateResult[Stream[A], A, Stream[A]]] = as match {
      case Stream.Empty => M.point(StateProducing(Stream(v), Stream.Empty))
      case s@(x #:: _) => if (f(x, v)) M.point(StateProducing(v #:: s, Stream.Empty))
                          else M.point(StateProducing(Stream(v), Stream(s)))
    }
    def close(s: => Stream[A]) = M.point(Stream(s))
    conduitState(Stream.empty, push, close)
  }

  def zip[F[_], A, B](f1: Source[F, A], f2: Source[F, B])(implicit M: Monad[F]): Source[F, (A, B)] = (f1, f2) match {
    case (Done(_, ()), Done(_, ())) => Done(None, ())
    case (Done(_, ()), HaveOutput(_, close, _)) => PipeM(M.bind(close)(_ => M.point(Done(None, ()))), close)
    case (HaveOutput(_, close, _), Done(_, ())) => PipeM(M.bind(close)(_ => M.point(Done(None, ()))), close)
    case (Done(_, ()), PipeM(_, close)) => PipeM(M.bind(close)(_ => M.point(Done(None, ()))), close)
    case (PipeM(_, close), Done(_, ())) => PipeM(M.bind(close)(_ => M.point(Done(None, ()))), close)
    case (PipeM(mx, closex), PipeM(my, closey)) => PipeM(M.map2(mx, my)((a, b) => zip(a, b)), M.bind(closex)(_ => closey))
    case (PipeM(mx, closex), y@HaveOutput(_, closey, _)) => PipeM(M.map(mx)(x => zip(x, y)), M.bind(closex)(_ => closey))
    case (x@HaveOutput(_, closex, _), PipeM(my, closey)) => PipeM(M.map(my)(y => zip(x, y)), M.bind(closex)(_ => closey))
    case (HaveOutput(srcx, closex, x),HaveOutput(srcy, closey, y)) => HaveOutput(zip(srcx, srcy), M.bind(closex)(_ => closey), (x, y))
    case _ => sys.error("")
  }

  /**
   * Ensures that the inner sink consumes no more than the given number of values.
   * This does not enure that the sink consumes all of those values.
   */
  def isolate[F[_], A](count: Int)(implicit M: Monad[F]): Conduit[A, F, A] = {
    def close(s: => Int): F[Stream[A]] = M.point(Stream.Empty)
    def push(c: => Int, x: => A): F[ConduitStateResult[Int, A, A]] =
      if (c <= 0) M.point(StateFinished(Some(x), Stream.Empty))
      else {
        val c1 = c - 1
        if (c1 <= 0) M.point(StateFinished(None, Stream(x)))
        else M.point(StateProducing(c1, Stream(x)))
      }
    conduitState(count, push, close)
  }
}
