package conduits

import pipes._
import Pipe._
import scalaz.{Forall, Monoid, MonadTrans, Monad}

/**
 * The underlying datatype for all the types in this package.  In has four
 * type parameters:
 *
 * <ol>
 *  <li> A is the type of values for this Pipe's input stream.
 *  <li> B is the type of values for this Pipe's output stream.
 *  <li> F the underlying Monad.
 *  <li> R the result type.
 * </ol>
 *
 * Note that the types `B` and `R` not the same: `B` is the type of values that
 * this Pipe produces and sends downstream. `R` is the final output of this Pipe.
 *
 * Pipes can be composed using the `pipe` functions.
 * The output type of the left pipe much match the input type of the left pipe, and the result
 * type of the left pipe must be [[scala.Unit]]. This is due to the fact that any
 * result produced by the left pipe must be discarded in favor of the result of
 * the right pipe.
 *
 * @tparam A The type of input received from upstream pipes
 * @tparam B The type of output delivered to downstream pipes
 * @tparam F The base monad
 * @tparam R The type of the monad's final result
 */
sealed trait Pipe[A, B, F[_], R] {

  def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => F[R], => B) => Z
              , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
              , done: (=> Option[A], => R) => Z
              , pipeM: (=> F[Pipe[A, B, F, R]], => F[R]) => Z): Z

  /**
   * Perform any close actions available for the given Pipe.
   */
  def pipeClose(implicit F: Monad[F]): F[R] = fold(
    haveOutput = (_, c, _) => c
    , needInput = (_, p) => p.pipeClose
    , done = (_, r) => F.point(r)
    , pipeM = (_, c) => c
  )

  def pipePush(i: => A)(implicit F: Monad[F]): Pipe[A, B, F, R] = fold(
    haveOutput = (p, c, o) => HaveOutput(p.pipePush(i), c, o)
    , needInput = (p, _) => p.apply(i)
    , done = (_, r) => Done(Some(i), r)
    , pipeM = (mp, c) => PipeM(F.map(mp)(p => p.pipePush(i)), c)
  )

  def map[S](f: (R) => S)(implicit F: Monad[F]): Pipe[A, B, F, S] = flatMap(a => Done(None, f(a)))

  def flatMap[S](f: (R) => Pipe[A, B, F, S])(implicit F: Monad[F]): Pipe[A, B, F, S] = {
    def through(p: Pipe[A, B, F, R]): Pipe[A, B, F, S] = p.fold(
      haveOutput = (p, c, o) => HaveOutput(through(p), F.bind(c)(r => f(r) pipeClose), o)
      , needInput = (p, c) => NeedInput(i => through(p.apply(i)), through(c))
      , done = (oi, x) => oi match {
        case Some(i) => f(x).pipePush(i)
        case None => f(x)
      }
      , pipeM = (mp, c) => PipeM(F.map(mp)(p1 => through(p1)), F.bind(c)(r => f(r) pipeClose))
    )
    through(this)
  }

  def transPipe[G[_]](f: Forall[({type λ[A] = F[A] => G[A]})#λ])(implicit M: Monad[F], G: Monad[G]): Pipe[A, B, G, R] = {
    def go(pipe: Pipe[A, B, F, R]): Pipe[A, B, G, R] = pipe match {
      case Done(a, b) => Done(a, b)
      case NeedInput(p, c) => NeedInput[A, B, G, R](i => go(p(i)), go(c))
      case HaveOutput(p, c, o) => HaveOutput[A, B, G, R](go(p), f.apply(c), o)
      case PipeM(mp, c) => PipeM[A, B, G, R](f.apply(M.map(mp)(p => go(p))), f.apply(c))
    }
    go(this)
  }

  def mapOutput[C](f: B => C)(implicit M: Monad[F]): Pipe[A, C, F, R] = {
    def go(pipe: Pipe[A, B, F, R]): Pipe[A, C, F, R] = pipe match {
      case Done(a, b) => Done(a, b)
      case NeedInput(p, c) => NeedInput[A, C, F, R](i => go(p(i)), go(c))
      case HaveOutput(p, c, o) => HaveOutput[A, C, F, R](go(p), c, f(o))
      case PipeM(mp, c) => PipeM[A, C, F, R](M.map(mp)(p => go(p)), c)
    }
    go(this)
  }
}

object Pipe {

  import FoldUtils._

  /**
   * Provide new output to be sent downstream. HaveOutput has three fields: the next
   * pipe to be used, an early-close function and the output value.
   */
  object HaveOutput {
    def apply[A, B, F[_], R](p: => Pipe[A, B, F, R], r: => F[R], b: => B) = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => F[R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> Option[A], => R) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => F[R]) => Z): Z = haveOutput(p, r, b)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(Pipe[A, B, F, R], F[R], B)] =
      p.fold((p, r, b) => Some(p, r, b), ToNone2, ToNone2, ToNone2)
  }

  /**
   * Request more input from upstream. The first field takes a new input value and provides a new Pipe.
   * The second is for early termination: it gives a new Pipe that takes no input from upstream.
   * This allows a Pipe to provide a final stream of output values after no more input is available
   * from upstream.
   */
  object NeedInput {
    def apply[A, B, F[_], R](aw: => A => Pipe[A, B, F, R], p: => Pipe[A, B, F, R]) = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => F[R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> Option[A], => R) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => F[R]) => Z): Z = needInput(aw, p)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(A => Pipe[A, B, F, R], Pipe[A, B, F, R])] =
      p.fold(ToNone3, (f, p) => Some(f, p), ToNone2, ToNone2)
  }

  /**
   * The processing of this Pipe is complete. The first field provides an (optional) leftover
   * input value, and the second field provides the final result.
   */
  object Done {
    def apply[A, B, F[_], R](i: => Option[A], r: => R) = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => F[R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> Option[A], => R) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => F[R]) => Z): Z = done(i, r)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(Option[A], R)] =
      p.fold(ToNone3, ToNone2, (i, r) => Some(i, r), ToNone2)
  }

  /**
   * Require running of a monadic action to get the next Pipe. The first field represents
   * this action, and the second field provides an early cleanup function.
   */
  object PipeM {
    def apply[A, B, F[_], R](pm: => F[Pipe[A, B, F, R]], fr: => F[R]) = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => F[R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> Option[A], => R) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => F[R]) => Z): Z = pipeM(pm, fr)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(F[Pipe[A, B, F, R]], F[R])] =
      p.fold(ToNone3, ToNone2, ToNone2, (i, r) => Some(i, r))
  }
}


trait PipeInstances {
  implicit def pipeMonad[I, O, F[_]](implicit F0: Monad[F]): Monad[({type l[r] = Pipe[I, O, F, r]})#l] = new Monad[({type l[r] = Pipe[I, O, F, r]})#l] {
    def bind[A, B](fa: Pipe[I, O, F, A])(f: (A) => Pipe[I, O, F, B]): Pipe[I, O, F, B] = fa flatMap f

    def point[A](a: => A) = Done(None, a)
  }

  implicit def pipeMonadTrans[I, O]: MonadTrans[({type l[a[_], b] = Pipe[I, O, a, b]})#l] = new MonadTrans[({type l[a[_], b] = Pipe[I, O, a, b]})#l] {
    implicit def apply[M[_]](implicit M0: Monad[M]): Monad[({type l[a] = Pipe[I, O, M, a]})#l] = pipeMonad[I, O, M]

    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): Pipe[I, O, G, A] = PipeM(M.map(ga)(a => pipeMonad[I, O, G].point(a)), ga)
  }

  implicit def pipeMonoid[A, B, F[_]](implicit F: Monad[F]): Monoid[Pipe[A, B, F, Unit]] = new Monoid[Pipe[A, B, F, Unit]] {
    def zero = pipeMonad[A, B, F].point(())

    def append(f1: Pipe[A, B, F, Unit], f2: => Pipe[A, B, F, Unit]) = f1 flatMap (_ => f2)
  }
}

trait PipeFunctions {
  /**
   * Composes two pipes together into a complete Pipe. The left Pipe will
   * be automatically closed when the right Pipe finishes. Any leftovers from the right
   * Pipe are discarded on finishing.
   */
  def pipe[A, B, C, F[_], R](p1: => Pipe[A, B, F, Unit], p2: => Pipe[B, C, F, R])(implicit F: Monad[F]): Pipe[A, C, F, R] =
    pipeResume(p1, p2) flatMap (pr =>
      pipeMonadTrans.liftM(pr._1.pipeClose) flatMap (_ => pipes.pipeMonad[A, C, F].point(pr._2))
    )

  /**
   * Similar to `pipe` but retain both the left pipe and any leftovers from the right pipe. The two components
   * are combined into a single new Pipe and returned, together with the result of the right pipe.
   *
   * Composition is biased towards checking the right Pipe first to avoid pulling
   * data that is not needed. Doing so could cause data loss.
   */
  def pipeResume[A, B, C, F[_], R](p1: => Pipe[A, B, F, Unit], p2: => Pipe[B, C, F, R])(implicit F: Monad[F]): Pipe[A, C, F, (Pipe[A, B, F, Unit], R)] = (p1, p2) match {
    //both pipes finished, return leftover of left pipe and leftovers of right pipe in the result.
    case (Done(leftoverl, ()), Done(leftoverr, r)) => leftoverr match {
      case None => Done(leftoverl, (pipes.pipeMonad[A, B, F].point(()), r))
      case Some(i) => Done(leftoverl, (HaveOutput(Done(None, ()), F.point(()), i), r))
    }
    //right pipe is done, terminate and return leftovers.
    case (left, Done(leftoverr, r)) => leftoverr match {
      case None => Done(None, (left, r))
      case Some(i) => Done(None, (HaveOutput(left, left.pipeClose, i), r))
    }
    //left pipe needs input, ask for it
    case (NeedInput(p, c), right) => NeedInput(a => pipeResume(p(a), right)
      , pipeResume(c, right).flatMap(pr => {
        pipeMonadTrans.liftM(pr._1.pipeClose)
        pipes.pipeMonad[A, C, F].point((pipes.pipeMonad[A, B, F].point(()), pr._2))
      }))
    //left pipe has output, right pipe wants it
    case (HaveOutput(lp, _, a), NeedInput(rp, _)) => pipeResume(lp, rp(a))
    //right pipe needs to run a monadic action
    case (left, PipeM(mp, c)) => PipeM(F.map(mp)(p => pipeResume(left, p)), F.map(c)(r => (left, r)))
      //right Pipe has some output, provide it downstream and continue.
    case (left, HaveOutput(p, c, o)) => HaveOutput(pipeResume(left, p), F.map(c)(r => (left, r)), o)
    //left pipe is Done, right pipe needs input. Tell the right pipe there is no more input
    //eventually replace its leftovers with the left pipe leftover
    case (Done(l, ()), NeedInput(_, c)) => replaceLeftOver(l, c).map(r => (pipes.pipeMonad[A, B, F].point(()), r))
    //left pipe needs to run a monadic action
    case (PipeM(mp, c), right) => PipeM(F.map(mp)(p => pipeResume(p, right))
      , F.bind(c)(_ => F.map(right.pipeClose)(r => (pipes.pipeMonad[A, B, F].point(()), r)))
    )

    case _ => sys.error("TODO")
  }

  private def replaceLeftOver[A, B, C, F[_], R](l: => Option[A], p1: => Pipe[C, B, F, R])(implicit F: Monad[F]): Pipe[A, B, F, R] = p1.fold(
    haveOutput = (p, c, o) => HaveOutput(replaceLeftOver(l, p), c, o)
    , needInput = (_, c) => replaceLeftOver(l, c)
    , done = (_, r) => Done(l, r)
    , pipeM = (mp, c) => PipeM(F.map(mp)(p => replaceLeftOver(l, p)), c)
    )

  /**
   * Run a complete pipeline until processing completes.
   */
  def runPipe[F[_], R](p: => Pipe[Zero, Zero, F, R])(implicit F: Monad[F]): F[R] = p.fold(
    haveOutput = (_, c, _) => c
    , needInput = (_, c) => runPipe(c)
    , done = (_, r) => F.point(r)
    , pipeM = (mp, _) => F.bind(mp)(p1 => runPipe(p1))
  )

  /**
   * Send a single output value downstream.
   */
  def yieldp[A, B, F[_]](b: => B)(implicit F: Monad[F]): Pipe[A, B, F, Unit] =
    HaveOutput(Done(None, ()), F.point(()), b)

  /**
   * Wait for a single input value from upstream, and remove it from the
   * stream. Returns [[scala.None]] if no more data is available.
   */
  def await[A, B, F[_]](implicit F: Monad[F]): Pipe[A, B, F, Option[A]] =
    NeedInput(a => Done(None, Some(a)), Done(None, None))

  /**
   * Wait for a single input value from upstream, and remove it from the
   * stream. Returns [[scala.None]] if no more data is available.
   */
  def hasInput[A, B, F[_]](implicit F: Monad[F]): Pipe[A, B, F, Boolean] =
    NeedInput(i => Done(Some(i), true), Done(None, false))

  /**
   * The [[conduits.pipes.Zero]] type parameter for Sink in the output, makes
   * it difficult to compose it with Sources and Conduits. This function replaces
   * that parameter with a free variable. The function is essentially `id`: it
   * only modifies the types, not the actions performed.
   *
   */
  def sinkToPipe[A, B, F[_], R](s: Sink[A, F, R])(implicit F: Monad[F]): Pipe[A, B, F, R] = s.fold(
    haveOutput = (_, c, _) => pipeMonadTrans.liftM(c)
    , needInput = (p, c) => NeedInput(i => sinkToPipe(p.apply(i)), sinkToPipe(c))
    , done = (i, r) => Done(i, r)
    , pipeM = (mp, c) => PipeM(F.map(mp)(p => sinkToPipe(p)), c)
  )
}

object pipes extends PipeInstances with PipeFunctions {

  /**
   * The uninhabited type.
   */
  case class Zero(z: Zero)

  /**
   * 'ex contradictione sequitur quodlibet'
   */
  def absurd[A](z: Zero): A = absurd(z)

  /**
   * A Pipe that produces a stream of output values, without consuming any input.
   * A Source does not produce a final result, thus the result parameter is [[scala.Unit]].
   */
   type Source[F[_], A] = Pipe[Zero, A, F, Unit]

  /**
   * A Pipe that consumes a stream of input values and produces a final result.
   * It cannot produce any output values, and thus the output parameter is [[conduits.pipes.Zero]]
   */
   type Sink[A, F[_], R] = Pipe[A, Zero, F, R]

  /**
   * A Pipe that consumes a stream of input values and produces a stream
   * of output values. It does not produce a result value, and thus the result
   * parameter is [[scala.Unit]]
   */
   type Conduit[A, F[_], B] = Pipe[A, B, F, Unit]

}