package conduits

import conduits.FoldUtils._
import scala.Some
import scalaz.{Monoid, MonadTrans, Monad}
import conduits.Pipe.HaveOutput
import conduits.Finalize.{FinalizePure, FinalizeM}

/**
 * User: arjan
 */
import TPipe._

/**
 * A terminating pipe. As opposed to normal `Pipe`s, `TPipe`s will stop
 * running as soon as either their upstream or downstream components terminate.
 * This approach can simplify certain use cases (e.g., infinite producers).
 */
sealed trait TPipe[A, B, F[_], R] {
  def fold[Z](haveOutput: (=> TPipe[A, B, F, R], => B) => Z
              , needInput: (=> A => TPipe[A, B, F, R]) => Z
              , done: (=> R) => Z
              , leftover: (=>  TPipe[A, B, F, R], => A) => Z
              , pipeM: (=> F[TPipe[A, B, F, R]]) => Z): Z

  def map[S](f: (R) => S)(implicit F: Monad[F]): TPipe[A, B, F, S] = flatMap(a => TDone(f(a)))

  def flatMap[S](f: (R) => TPipe[A, B, F, S])(implicit F: Monad[F]): TPipe[A, B, F, S] = this match {
    case THaveOutput(p, o) => THaveOutput(p flatMap f, o)
    case TNeedInput(push) => TNeedInput(i => push(i) flatMap f)
    case TDone(r) => f(r)
    case TPipeM(mp) => TPipeM(F.map(mp)(p => p.flatMap(f)))
    case TLeftover(p, i) => TLeftover(p flatMap f, i)
  }
}

object TPipe extends TPipeInstances with TPipeFunctions {
  /**
   * Provide new output to be sent downstream. HaveOutput has three fields: the next
   * pipe to be used, an early-close function and the output value.
   */
  object THaveOutput {
    def apply[A, B, F[_], R](p: => TPipe[A, B, F, R], b: => B): TPipe[A, B, F, R] = new TPipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> TPipe[A, B, F, R], => B) => Z
                  , needInput: (=> A => TPipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> TPipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[TPipe[A, B, F, R]]) => Z): Z = haveOutput(p, b)
    }

    def unapply[A, B, F[_], R](p: TPipe[A, B, F, R]): Option[(TPipe[A, B, F, R], B)] =
      p.fold((p, b) => Some(p, b), ToNone1, ToNone1, ToNone2, ToNone1)
  }

  /**
   * Request more input from upstream. The first field takes a new input value and provides a new Pipe.
   * The second is for early termination: it gives a new Pipe that takes no input from upstream.
   * This allows a Pipe to provide a final stream of output values after no more input is available
   * from upstream.
   */
  object TNeedInput {
    def apply[A, B, F[_], R](aw: => A => TPipe[A, B, F, R]): TPipe[A, B, F, R] = new TPipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> TPipe[A, B, F, R], => B) => Z
                  , needInput: (=> A => TPipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> TPipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[TPipe[A, B, F, R]]) => Z): Z = needInput(aw)
    }

    def unapply[A, B, F[_], R](p: TPipe[A, B, F, R]): Option[(A => TPipe[A, B, F, R])] =
      p.fold(ToNone2, Some(_), ToNone1, ToNone2, ToNone1)
  }

  /**
   * The processing of this Pipe is complete. The first field provides an (optional) leftover
   * input value, and the second field provides the final result.
   */
  object TDone {
    def apply[A, B, F[_], R](r: => R): TPipe[A, B, F, R] = new TPipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> TPipe[A, B, F, R], => B) => Z
                  , needInput: (=> A => TPipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> TPipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[TPipe[A, B, F, R]]) => Z): Z = done(r)
    }

    def unapply[A, B, F[_], R](p: TPipe[A, B, F, R]): Option[R] =
      p.fold(ToNone2, ToNone1, Some(_), ToNone2, ToNone1)
  }

  object TLeftover {
    def apply[A, B, F[_], R](p: => TPipe[A, B, F, R], i: => A): TPipe[A, B, F, R] = new TPipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> TPipe[A, B, F, R], => B) => Z
                  , needInput: (=> A => TPipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> TPipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[TPipe[A, B, F, R]]) => Z): Z = leftover(p, i)
    }

    def unapply[A, B, F[_], R](p: TPipe[A, B, F, R]): Option[(TPipe[A, B, F, R], A)] =
      p.fold(ToNone2, ToNone1, ToNone1, (p, i) => Some(p, i), ToNone1)
  }

  /**
   * Require running of a monadic action to get the next Pipe. The first field represents
   * this action, and the second field provides an early cleanup function.
   */
  object TPipeM {
    def apply[A, B, F[_], R](pm: => F[TPipe[A, B, F, R]]): TPipe[A, B, F, R] = new TPipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> TPipe[A, B, F, R], => B) => Z
                  , needInput: (=> A => TPipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> TPipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[TPipe[A, B, F, R]]) => Z): Z = pipeM(pm)
    }

    def unapply[A, B, F[_], R](p: TPipe[A, B, F, R]): Option[F[TPipe[A, B, F, R]]] =
      p.fold(ToNone2, ToNone1, ToNone1, ToNone2, f => Some(f))
  }
}
import TPipe._
import Pipe._

trait TPipeInstances {
  implicit def tpipeMonad[I, O, F[_]](implicit F0: Monad[F]): Monad[({type l[r] = TPipe[I, O, F, r]})#l] = new Monad[({type l[r] = TPipe[I, O, F, r]})#l] {
    def bind[A, B](fa: TPipe[I, O, F, A])(f: (A) => TPipe[I, O, F, B]): TPipe[I, O, F, B] = fa flatMap f

    def point[A](a: => A) = TDone(a)
  }

  implicit def tpipeMonadTrans[I, O]: MonadTrans[({type l[a[_], b] = TPipe[I, O, a, b]})#l] = new MonadTrans[({type l[a[_], b] = TPipe[I, O, a, b]})#l] {
    implicit def apply[M[_]](implicit M0: Monad[M]): Monad[({type l[a] = TPipe[I, O, M, a]})#l] = tpipeMonad[I, O, M]

    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): TPipe[I, O, G, A] = TPipeM(M.map(ga)(a => tpipeMonad[I, O, G].point(a)))
  }

  implicit def tpipeMonoid[A, B, F[_]](implicit F: Monad[F]): Monoid[TPipe[A, B, F, Unit]] = new Monoid[TPipe[A, B, F, Unit]] {
    def zero = tpipeMonad[A, B, F].point(())

    def append(f1: TPipe[A, B, F, Unit], f2: => TPipe[A, B, F, Unit]) = f1 flatMap (_ => f2)
  }
}

trait TPipeFunctions {

  def tawait[F[_], A, B]: TPipe[A, B, F, A] = TNeedInput(TDone(_))

  def tleftover[F[_], A, B](i: A): TPipe[A, B, F, Unit] = TLeftover(TDone(()), i)

  def toPipe[F[_], A, B](tpipe: TPipe[A, B, F, Unit])(implicit M: Monad[F]): Pipe[A, B, F, Unit] =
    toPipeFinalize(FinalizePure(()), tpipe)

  def toPipeFinalize[F[_], A, B, R](fin: Finalize[F, R], tpipe: TPipe[A, B, F, Unit])(implicit M: Monad[F]): Pipe[A, B, F, R] = {
    def go(tp: TPipe[A, B, F, Unit]): Pipe[A, B, F, R] = tp match {
      case THaveOutput(p, o) => HaveOutput(go(p), fin, o)
      case TNeedInput(p) => NeedInput(i => go(p(i)), done)
      case TDone(()) => done
      case TPipeM(mp) => PipeM[A, B, F, R](M.map(mp)(go(_)), fin)
      case TLeftover(p, i) => Leftover(go(p), i)
    }

    def done: Pipe[A, B, F, R] = fin match {
      case FinalizeM(f) => PipeM[A, B, F, R](M.map(f)(Done(_)), fin)
      case FinalizePure(r) => Done(r)
    }
    go(tpipe)
  }
}