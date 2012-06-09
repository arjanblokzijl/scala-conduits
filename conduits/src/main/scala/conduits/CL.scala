package conduits

import empty.Void
import resourcet.resource
import scalaz.std.stream._
import scalaz._
import collection.immutable.Stream
import conduits.pipes._
import Finalize._

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
    def push1(acc: B)(input: A): Sink[A, F, B] = go(f(acc, input))
    def go(accum: => B): Sink[A, F, B] = NeedInput(push1(accum), pipeMonad[A, Void, F].point(accum))
    go(z)
  }

  /**
   * Generate a source from a seed value.
   */
  def unfold[F[_], A, B](f: B => Option[(A, B)])(b: => B)(implicit M: Monad[F]): Source[F, A] = {
    def go(seed: => B): Source[F, A] = {
      f(seed) match {
        case None => Done(None, ())
        case Some((a, seed1)) => HaveOutput(go(seed1), FinalizePure(()), a)
      }
    }
    go(b)
  }

  def enumFromTo[F[_], A](start: => A, stop: => A)(implicit M: Monad[F], O: scalaz.Order[A], EN: scalaz.Enum[A]): Source[F, A] = {
    def go(i: => A): Source[F, A] = {
      if (O.greaterThanOrEqual(i, stop)) HaveOutput(Done(None, ()), FinalizePure(()), i)
      else HaveOutput(go(EN.succ(i)), FinalizePure(()), i)
    }
    go(start)
  }

  def sum[F[_]](implicit M: Monad[F]): Sink[Int, F, Int] = foldLeft((0: Int))(_ + _)

  /**Take a single value from the stream, if available.*/
  def head[F[_], A](implicit M: Monad[F]): Pipe[A, Void, F, Option[A]] = await

  /**Look at the next value of the stream, if available. This does not alter the content of the stream*/
  def peek[F[_], A](implicit M: Monad[F]): Pipe[A, Void, F, Option[A]] =
    await[F, A, Void].flatMap(_.map(x => leftover[F, A, Void](x).flatMap(_ => Done[A, Void, F, Option[A]](Some(x)))).getOrElse(Done[A, Void, F, Option[A]](None)))


  /**
   * takes the given number of elements of the Stream, and returns the elements in the given [[scalaz.Monoid]].
   */
  def takeM[F[_], M[_], A](n: Int)(implicit M: Monad[F], P: Pointed[M], MO: Monoid[M[A]]): Sink[A, F, M[A]] = {
    def go(count: Int, acc: M[A]) = NeedInput(push(count, acc), pipeMonad[A, Void, F].point(acc))
    def push(count: Int, acc: M[A])(x: A): Sink[A, F, M[A]] = {
       if (count <= 0) Done(acc)
       else {
         val count1 = count - 1
         if (count1 <= 0) Done(MO.append(acc, P.point(x)))
         else NeedInput(push(count1, MO.append(acc, P.point(x))),  pipeMonad[A, Void, F].point(MO.append(acc, P.point(x))))
       }
    }
    go(n, MO.zero)
  }

  /**
   * Calls `takeM` using the Identity Monad.
   */
  def take[M[_], A](n: Int)(implicit P: Pointed[M], MO: Monoid[M[A]]): Sink[A, Id, M[A]] = {
    implicit val idM = Id.id
    takeM(n)
  }

  /**
   * Takes the elements from the stream and returns them in a [[scalaz.DList]].
   * This function is exposed since it is slightly more efficient than the general `takeM`
   * which needs to append to Monoids on each invocation, while this version can just append
   * the given element to the DList, which is effectively an O(1) operation.
   */
  def takeDList[F[_], A](n: Int)(implicit M: Monad[F]): Sink[A, F, DList[A]] = {
    def go(count: Int, acc: => DList[A]) = NeedInput(push(count, acc), pipeMonad[A, Void, F].point(acc))
    def push(count: Int, acc: => DList[A])(x: A): Sink[A, F, DList[A]] =
      if (count <= 0) Done(acc)
      else {
        val count1 = count - 1
        if (count1 <= 0) Done(acc :+ x)
        else NeedInput(push(count1, acc :+ x), pipeMonad[A, Void, F].point(acc))
      }

    go(n, DList())
  }


  //TODO the most efficient, but dangerous to expose this
  private[conduits] def takeBuffer[F[_], A](n: Int)(implicit M: Monad[F]): Sink[A, F, Seq[A]] = {
    def go(count: Int, acc: collection.mutable.ListBuffer[A]): Sink[A, F, Seq[A]] = NeedInput(push(count, acc), pipeMonad[A, Void, F].point(acc))
    def push(count: Int, acc: collection.mutable.ListBuffer[A])(x: A): Sink[A, F, Seq[A]] = {
       if (count <= 0) Done(acc)
       else {
         val count1 = count - 1
         if (count1 <= 0) Done(acc += x)
         else NeedInput(push(count1, acc += x), pipeMonad[A, Void, F].point(acc += x))
       }
    }
    go(n, collection.mutable.ListBuffer[A]())
  }

  def drop[F[_], A](count: Int)(implicit M: Monad[F]): Sink[A, F, Unit] = {
    def push(i: A): Sink[A, F, Unit] = (count - 1) match {
      case 0 => Done(())
      case n => drop(n)
    }
    count match {
      case n if (n <= 0) => NeedInput(i => leftover(i), Done(()))
      case c => NeedInput(push, NeedInput(push, Done(())))
    }
  }

  def consumeDlist[F[_], A](implicit M: Monad[F]): Sink[A, F, DList[A]] = {
    def go(acc: DList[A]): Sink[A, F, DList[A]] = NeedInput(push(acc), pipeMonad[A, Void, F].point(acc))
    def push(acc: DList[A])(x: A): Sink[A, F, DList[A]] = go(acc :+ x)
    go(DList())
  }

  def consume[F[_], A](implicit M: Monad[F]): Pipe[A, Void, F, Stream[A]] = {
    def go(acc: Stream[A]): Sink[A, F, Stream[A]] = NeedInput(push(acc), pipeMonad[A, Void, F].point(acc))
    def push(acc: Stream[A])(x: A): Sink[A, F, Stream[A]] = go(streamInstance.plus(acc, Stream(x)))
    def loop(acc: Stream[A]): Pipe[A, Void, F, Stream[A]] = {
      await[F, A, Void].flatMap(i => i.map(x => loop(streamInstance.plus(acc, Stream(x)))).getOrElse(Done(acc)))
    }
    loop(Stream.empty[A])
  }

  def filter[F[_], A](f: A => Boolean)(implicit M: Monad[F]): Conduit[A, F, A] = {
    def close = pipeMonoid[A, A, F].zero
    def push: A => Conduit[A, F, A] = i =>
      if (f(i)) HaveOutput(NeedInput(push, close), FinalizePure(()), i)
      else NeedInput(push, close)
    NeedInput(push, close)
  }

  def sourceList[F[_], A](l: => Stream[A])(implicit M: Monad[F]): Source[F, A] = yieldMany(l)

  /**
   * Apply a transformation to all values in a stream.
   */
  def map[F[_], A, B](f: A => B)(implicit M: Monad[F]): Conduit[A, F, B] = {
    def close = pipeMonoid[A, B, F].zero
    def push: A => Conduit[A, F, B] = i =>
      HaveOutput(NeedInput(push, close), FinalizePure(()), f(i))

    NeedInput(push, close)
  }

  /**
   * Apply a monadic transformation to all values in a stream.
   */
  def mapM[F[_], A, B](f: A => F[B])(implicit M: Monad[F]): Conduit[A, F, B] = {
    def close = pipeMonoid[A, B, F].zero
    def push: A => Conduit[A, F, B] = i =>
      PipeM(M.map(f(i))(o => HaveOutput(NeedInput(push, close), FinalizePure(()), o)), FinalizePure(()))

    NeedInput(push, close)
  }

  /**
   * Apply a monadic transformation to all values in a stream.
   */
  def mapM_[F[_], A, B](f: A => F[B])(implicit M: Monad[F]): Sink[A, F, Unit] = {
    def close = pipeMonad[A, Void, F].point(())
    def push: A => Sink[A, F, Unit] = i =>
      PipeM(M.map(f(i))(_ => NeedInput(push, close)), FinalizePure(()))

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

  /**
   * concatMap with an accumulator
   */
  def concatMapAccum[F[_], A, B, AC](accum: AC, f: (AC, A )=> (AC, Stream[B]))(implicit M: Monad[F]): Conduit[A, F, B] = {
    def close(acc: => AC): F[Stream[B]] = M.point(Stream.Empty)
    def push(state: => AC, input: => A): F[ConduitStateResult[AC, A, B]] = {
      val (state1, result) = f(state, input)
      M.point(StateProducing(state1, result))
    }
    conduitState(accum, push, close)
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
    case (Done(()), Done(())) => Done(())
    case (Done(()), HaveOutput(_, close, _)) => PipeM(M.bind(runFinalize(close))(_ => M.point(Done(()))), close)
    case (HaveOutput(_, close, _), Done(())) => PipeM(M.bind(runFinalize(close))(_ => M.point(Done(()))), close)
    case (Done(()), PipeM(_, close)) => PipeM(M.bind(runFinalize(close))(_ => M.point(Done(()))), close)
    case (PipeM(_, close), Done(())) => PipeM(M.bind(runFinalize(close))(_ => M.point(Done(()))), close)
    case (PipeM(mx, closex), PipeM(my, closey)) => PipeM(M.map2(mx, my)((a, b) => zip(a, b)), closex.flatMap(_ => closey))
    case (PipeM(mx, closex), y@HaveOutput(_, closey, _)) => PipeM(M.map(mx)(x => zip(x, y)), closex.flatMap(_ => closey))
    case (x@HaveOutput(_, closex, _), PipeM(my, closey)) => PipeM(M.map(my)(y => zip(x, y)), closex.flatMap(_ => closey))
    case (HaveOutput(srcx, closex, x),HaveOutput(srcy, closey, y)) => HaveOutput(zip(srcx, srcy), closex.flatMap(_ => closey), (x, y))
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

  /**
   * Ignore the remainder of values in the source. Particularly useful when
   * combined with 'isolate'.
   */
  def sinkNull[F[_], A](implicit M: Monad[F]): Sink[A, F, Unit] =
    NeedInput(_ => sinkNull, pipeMonad[A, Void, F].point(()))

  def zipSinks[F[_], A, B, C](f1: Sink[A, F, B], f2: Sink[A, F, C])(implicit M: Monad[F]): Sink[A, F, (B, C)] =
    zipSinks1(scalaz.Ordering.EQ)(f1, f2)

  import Void._
  private def zipSinks1[F[_], A, B, C](by: scalaz.Ordering)(f1: Sink[A, F, B], f2: Sink[A, F, C])(implicit M: Monad[F]): Sink[A, F, (B, C)] = (f1, f2) match {
    case (PipeM(mpx, mx), py) => PipeM(M.map(mpx)(x => zipSinks1(by)(x, py)), finalizeMonad[F].map2(mx, py.pipeClose)((x, y) => (x, y)))
    case (px, PipeM(mpy, my)) => PipeM(M.map(mpy)(y => zipSinks1(by)(px, y)), finalizeMonad[F].map2(px.pipeClose, my)((x, y) => (x, y)))
    case (Done(x), Done(y)) => Done((x, y))
    case (NeedInput(fpx, px), NeedInput(fpy, py)) => NeedInput(i => zipSinks1(scalaz.Ordering.EQ)(fpx(i), fpy(i)), zipSinks1(scalaz.Ordering.EQ)(px, py))
    case (NeedInput(fpx, px), py) => NeedInput(i => zipSinks1(scalaz.Ordering.GT)(fpx(i), py), zipSinks1(scalaz.Ordering.EQ)(px, py))
    case (px, NeedInput(fpy, py)) => NeedInput(i => zipSinks1(scalaz.Ordering.LT)(px, fpy(i)), zipSinks1(scalaz.Ordering.EQ)(px, py))
    case (HaveOutput(_, _, o), _) => absurd(o)
    case (_, HaveOutput(_, _, o)) => absurd(o)
  }
}
