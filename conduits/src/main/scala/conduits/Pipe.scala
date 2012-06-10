package conduits

import empty.Void
import pipes._
import Pipe._
import scalaz.{Forall, Monoid, MonadTrans, Monad}
import scalaz.std.function._
import scalaz.Free.{Trampoline, return_, suspend}
import scalaz.effect._
import javax.print.attribute.standard.Finishings
import resourcet.{ReleaseKey, MonadResource, MonadThrow}

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
  import Finalize._
  def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => Finalize[F, R], => B) => Z
              , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
              , done: (=> R) => Z
              , leftover: (=>  Pipe[A, B, F, R], => A) => Z
              , pipeM: (=> F[Pipe[A, B, F, R]], => Finalize[F, R]) => Z): Z

  /**
   * Perform any close actions available for the given Pipe.
   */
  def pipeClose(implicit F: Monad[F]): Finalize[F, R] = fold(
    haveOutput = (_, c, _) => c
    , needInput = (_, p) => p.pipeClose
    , done = r => FinalizePure(r)
    , leftover = (p, r) => p.pipeClose
    , pipeM = (_, c) => c
  )

  def pipePush(i: => A)(implicit F: Monad[F]): Pipe[A, B, F, R] = fold(
    haveOutput = (p, c, o) => HaveOutput(p.pipePush(i), c, o)
    , needInput = (p, _) => p.apply(i)
    , done = r => Done(r)
    , leftover = (p, i) => Leftover(p, i)
    , pipeM = (mp, c) => PipeM(F.map(mp)(p => p.pipePush(i)), c)
  )

  def pipePushStrip(i: => A)(implicit F: Monad[F]): Pipe[A, B, F, R] = pipePush(i) match {
    case Leftover(p1, _) => p1
    case p1 => p1
  }

  def map[S](f: (R) => S)(implicit F: Monad[F]): Pipe[A, B, F, S] = flatMap(a => Done(f(a)))

  def flatMap[S](f: (R) => Pipe[A, B, F, S])(implicit F: Monad[F]): Pipe[A, B, F, S] = {
    def through(p: => Pipe[A, B, F, R]): Pipe[A, B, F, S] = p.fold(
      haveOutput = (p, c, o) => HaveOutput(through(p), c.flatMap(r => f(r) pipeClose), o)
      , needInput = (p, c) => NeedInput(i => through(p.apply(i)), c.flatMap(i => noInput(f(i))))
      , done = r => f(r)
      , leftover = (p, i) => Leftover(p.flatMap(f), i)
      , pipeM = (mp, c) => PipeM(F.map(mp)(p1 => through(p1)), c.flatMap(r => f(r) pipeClose))
    )
    through(this)
  }

  /**
   * Transforms the Monad a Pipe lives in.
   */
  def transPipe[G[_]](f: Forall[({type λ[A] = F[A] => G[A]})#λ])(implicit M: Monad[F], G: Monad[G]): Pipe[A, B, G, R] = {
    def go(pipe: Pipe[A, B, F, R]): Pipe[A, B, G, R] = pipe match {
      case Done(r) => Done(r)
      case NeedInput(p, c) => NeedInput[A, B, G, R](i => go(p(i)), go(c))
      case HaveOutput(p, c, o) => HaveOutput[A, B, G, R](go(p), c.transFinalize(f), o)
      case PipeM(mp, c) => PipeM[A, B, G, R](f.apply(M.map(mp)(p => go(p))), c.transFinalize(f))
    }
    go(this)
  }

  def mapOutput[C](f: B => C)(implicit M: Monad[F]): Pipe[A, C, F, R] = {
    def go(pipe: Pipe[A, B, F, R]): Pipe[A, C, F, R] = pipe match {
      case Done(r) => Done(r)
      case NeedInput(p, c) => NeedInput[A, C, F, R](i => go(p(i)), go(c))
      case HaveOutput(p, c, o) => HaveOutput[A, C, F, R](go(p), c, f(o))
      case PipeM(mp, c) => PipeM[A, C, F, R](M.map(mp)(p => go(p)), c)
    }
    go(this)
  }

  /**
   * Add cleanup action to be run when the pipe cleans up.
   * @param cleanup True if the Pipe ran to completion, false for early termination.
   * @param M the Monad the Pipe lives in.
   */
  def addCleanup(cleanup: Boolean => F[Unit])(implicit M: Monad[F]): Pipe[A, B, F, R] = {
    def go(pipe: Pipe[A, B, F, R]): Pipe[A, B, F, R] = pipe match {
      case Done(r) => PipeM(M.map(cleanup(true))(_ => Done(r)), finalizeMonadTrans.liftM(cleanup(true)).flatMap(_ => FinalizePure(r)))
      case NeedInput(p, c) => NeedInput[A, B, F, R](i => go(p(i)), go(c))
      case HaveOutput(src, close, x) => HaveOutput[A, B, F, R](go(src), finalizeMonadTrans.liftM(cleanup(false)).flatMap(_ => close), x)
      case PipeM(msrc, close) => PipeM[A, B, F, R](M.map(msrc)(p => go(p)), finalizeMonadTrans.liftM(cleanup(false)).flatMap(_ => close))
    }
    go(this)
  }
}

import FoldUtils._
import Finalize._
sealed trait Finalize[F[_], R] {

  def map[S](f: R => S)(implicit M: Monad[F]): Finalize[F, S] = flatMap(r => FinalizePure(f(r)))

  def flatMap[S](f: R => Finalize[F, S])(implicit M: Monad[F]): Finalize[F, S] = this match {
    case FinalizePure(r) => f(r)
    case FinalizeM(fr) => FinalizeM(M.bind(fr)(r => f(r) match {
      case FinalizePure(x) => M.point(x)
      case FinalizeM(mx) => mx
    }))
  }

  def transFinalize[G[_]](f: Forall[({type λ[A] = F[A] => G[A]})#λ])(implicit M: Monad[F], G: Monad[G]): Finalize[G, R] = this match {
    case FinalizePure(r) => FinalizePure(r)
    case FinalizeM(mr) => FinalizeM(f.apply(mr))
  }

  def fold[Z](res: R => Z, ff: F[R] => Z): Z
}

object Finalize extends FinalizeFunctions with FinalizeInstances {
  object FinalizePure {
    def apply[F[_], R](r: R): Finalize[F, R] = new Finalize[F, R] {
      def fold[Z](res: R => Z, fr: F[R] => Z): Z = res(r)
    }
    def unapply[F[_], R](f: Finalize[F, R]): Option[R] = f.fold(Some(_), ToNone1)
  }
  object FinalizeM {
    def apply[F[_], R](fr: F[R]): Finalize[F, R] = new Finalize[F, R] {
      def fold[Z](res: R => Z, ff: F[R] => Z): Z = ff(fr)
    }
    def unapply[F[_], R](f: Finalize[F, R]): Option[F[R]] = f.fold(ToNone1, Some(_))
  }
}

object Pipe {

  /**
   * Provide new output to be sent downstream. HaveOutput has three fields: the next
   * pipe to be used, an early-close function and the output value.
   */
  object HaveOutput {
    def apply[A, B, F[_], R](p: => Pipe[A, B, F, R], r: => Finalize[F, R], b: => B): Pipe[A, B, F, R] = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => Finalize[F, R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> Pipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => Finalize[F, R]) => Z): Z = haveOutput(p, r, b)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(Pipe[A, B, F, R], Finalize[F, R], B)] =
      p.fold((p, r, b) => Some(p, r, b), ToNone2, ToNone1, ToNone2, ToNone2)
  }

  /**
   * Request more input from upstream. The first field takes a new input value and provides a new Pipe.
   * The second is for early termination: it gives a new Pipe that takes no input from upstream.
   * This allows a Pipe to provide a final stream of output values after no more input is available
   * from upstream.
   */
  object NeedInput {
    def apply[A, B, F[_], R](aw: => A => Pipe[A, B, F, R], p: => Pipe[A, B, F, R]): Pipe[A, B, F, R] = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => Finalize[F, R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> Pipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => Finalize[F, R]) => Z): Z = needInput(aw, p)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(A => Pipe[A, B, F, R], Pipe[A, B, F, R])] =
      p.fold(ToNone3, (f, p) => Some(f, p), ToNone1, ToNone2, ToNone2)
  }

  /**
   * The processing of this Pipe is complete. The first field provides an (optional) leftover
   * input value, and the second field provides the final result.
   */
  object Done {
    def apply[A, B, F[_], R](r: => R): Pipe[A, B, F, R] = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => Finalize[F, R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> Pipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => Finalize[F, R]) => Z): Z = done(r)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(R)] =
      p.fold(ToNone3, ToNone2, r => Some(r), ToNone2, ToNone2)
  }

  object Leftover {
    def apply[A, B, F[_], R](p: => Pipe[A, B, F, R], i: => A): Pipe[A, B, F, R] = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => Finalize[F, R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> Pipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => Finalize[F, R]) => Z): Z = leftover(p, i)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(Pipe[A, B, F, R], A)] =
      p.fold(ToNone3, ToNone2, ToNone1, (p, i) => Some(p, i), ToNone2)
  }

  /**
   * Require running of a monadic action to get the next Pipe. The first field represents
   * this action, and the second field provides an early cleanup function.
   */
  object PipeM {
    def apply[A, B, F[_], R](pm: => F[Pipe[A, B, F, R]], fr: => Finalize[F, R]): Pipe[A, B, F, R] = new Pipe[A, B, F, R] {
      def fold[Z](haveOutput: (=> Pipe[A, B, F, R], => Finalize[F, R], => B) => Z
                  , needInput: (=> A => Pipe[A, B, F, R], => Pipe[A, B, F, R]) => Z
                  , done: (=> R) => Z
                  , leftover: (=> Pipe[A, B, F, R], => A) => Z
                  , pipeM: (=> F[Pipe[A, B, F, R]], => Finalize[F, R]) => Z): Z = pipeM(pm, fr)
    }

    def unapply[A, B, F[_], R](p: Pipe[A, B, F, R]): Option[(F[Pipe[A, B, F, R]], Finalize[F, R])] =
      p.fold(ToNone3, ToNone2, ToNone1, ToNone2, (i, r) => Some(i, r))
  }
}


trait PipeInstances {
  implicit def pipeMonad[I, O, F[_]](implicit F0: Monad[F]): Monad[({type l[r] = Pipe[I, O, F, r]})#l] = new Monad[({type l[r] = Pipe[I, O, F, r]})#l] {
    def bind[A, B](fa: Pipe[I, O, F, A])(f: (A) => Pipe[I, O, F, B]): Pipe[I, O, F, B] = fa flatMap f

    def point[A](a: => A) = Done(a)
  }

  implicit def pipeMonadTrans[I, O]: MonadTrans[({type l[a[_], b] = Pipe[I, O, a, b]})#l] = new MonadTrans[({type l[a[_], b] = Pipe[I, O, a, b]})#l] {
    implicit def apply[M[_]](implicit M0: Monad[M]): Monad[({type l[a] = Pipe[I, O, M, a]})#l] = pipeMonad[I, O, M]

    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): Pipe[I, O, G, A] = PipeM(M.map(ga)(a => pipeMonad[I, O, G].point(a)), FinalizeM(ga))
  }

  implicit def pipeMonoid[A, B, F[_]](implicit F: Monad[F]): Monoid[Pipe[A, B, F, Unit]] = new Monoid[Pipe[A, B, F, Unit]] {
    def zero = pipeMonad[A, B, F].point(())

    def append(f1: Pipe[A, B, F, Unit], f2: => Pipe[A, B, F, Unit]) = f1 flatMap (_ => f2)
  }
}

private[conduits] trait FinalizeMonad[F[_]] extends Monad[({type l[r] = Finalize[F, r]})#l] {
  implicit def F: Monad[F]
  def bind[A, B](fa: Finalize[F, A])(f: (A) => Finalize[F, B]): Finalize[F, B] = fa flatMap f
  def point[A](a: => A) = FinalizePure(a)
}

trait FinalizeInstances {

  implicit def finalizeMonad[F[_]](implicit M0: Monad[F]): Monad[({type l[r] = Finalize[F, r]})#l] = new FinalizeMonad[F] {
    implicit val F = M0
  }

  implicit def finalizeMonadTrans: MonadTrans[({type l[a[_], b] = Finalize[a, b]})#l] = new MonadTrans[({type l[a[_], b] = Finalize[a, b]})#l] {
    implicit def apply[M[_]](implicit M0: Monad[M]): Monad[({type l[a] = Finalize[M, a]})#l] = finalizeMonad[M]
    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): Finalize[G, A] = FinalizeM(ga)
  }

  implicit def finalizeMonadIO[F[_]](implicit M: MonadIO[F]): MonadIO[({type l[r] = Finalize[F, r]})#l]= new MonadIO[({type l[r] = Finalize[F, r]})#l] with FinalizeMonad[F] {
    implicit val F = M
    def liftIO[A](ioa: IO[A]) = FinalizeM(M.liftIO(ioa))
  }

  implicit def finalizeMonadThrow[F[_]](implicit MT: MonadThrow[F], F0: Monad[F]): MonadThrow[({type l[r] = Finalize[F, r]})#l] = new MonadThrow[({type l[r] = Finalize[F, r]})#l] {
    implicit def M = finalizeMonad[F]

    def monadThrow[A](e: Throwable) = finalizeMonadTrans.liftM(MT.monadThrow[A](e))
  }
}

trait FinalizeFunctions {
  def runFinalize[F[_], R](f: Finalize[F, R])(implicit F: Monad[F]): F[R] = f match {
    case FinalizePure(r) => F.point(r)
    case FinalizeM(mr) => mr
  }

  def combineFinalize[F[_], R](f1: Finalize[F, Unit], f2: Finalize[F, R])(implicit F: Monad[F]): Finalize[F, R] = (f1, f2) match {
    case (FinalizePure(()), f) => f
    case (FinalizeM(x), FinalizeM(y)) => FinalizeM(F.bind(x)(_ => y))
    case (FinalizeM(x), FinalizePure(y)) => FinalizeM(F.bind(x)(_ => F.point(y)))
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
      pipeMonadTrans.liftM(runFinalize(pr._1.pipeClose)) flatMap (_ => pipes.pipeMonad[A, C, F].point(pr._2))
    )

  /**
   * Similar to `pipe` but retain both the left pipe and any leftovers from the right pipe. The two components
   * are combined into a single new Pipe and returned, together with the result of the right pipe.
   *
   * Composition is biased towards checking the right Pipe first to avoid pulling
   * data that is not needed. Doing so could cause data loss.
   */
  def pipeResume[A, B, C, F[_], R](p1: Pipe[A, B, F, Unit], p2: Pipe[B, C, F, R])(implicit F: Monad[F]): Pipe[A, C, F, (Pipe[A, B, F, Unit], R)] = {
    def go(left: => Pipe[A, B, F, Unit], right: => Pipe[B, C, F, R]): Trampoline[Pipe[A, C, F, (Pipe[A, B, F, Unit], R)]] = right match {
      case Done(r) => return_(Done(left, r))
      case PipeM(mp, c) => return_(PipeM(F.map(mp)(p => pipeResume(left, p)), c.map(r => (left, r))))
      case HaveOutput(p, c, o) => return_(HaveOutput(pipeResume(left, p), c.map(r => (left, r)), o))
      case Leftover(p, i) => suspend(go(HaveOutput(left, left.pipeClose, i), p))
      case NeedInput(rp, rc) => left match {
        case Leftover(lp, i) => suspend(go(lp, NeedInput(rp, rc)).map(pr => pr.map{case (p, r) => (Leftover(p, i), r)}))
        case HaveOutput(lp, _, a) => suspend(go(lp, rp(a)))
        case NeedInput(p, c) => return_(NeedInput(a => pipeResume(p(a), right),
          pipeResume(c, right).flatMap(pr => {
            pipeMonadTrans.liftM(runFinalize(pr._1.pipeClose))
            pipes.pipeMonad[A, C, F].point((pipes.pipeMonad[A, B, F].point(()), pr._2))
          }))
        )
        case Done(()) => return_(noInput(rc).map(r => (pipes.pipeMonad[A, B, F].point(()), r)))
        case PipeM(mp, c) =>  return_(PipeM(F.map(mp)(p => pipeResume(p, right))
          , combineFinalize(c, right.pipeClose).flatMap(r => (FinalizePure(pipes.pipeMonad[A, B, F].point(()), r)))
        ))
      }
    }
    go(p1, p2).run
  }

  private[conduits] def noInput[A, B, C, F[_], R](p1: => Pipe[C, B, F, R])(implicit F: Monad[F]): Pipe[A, B, F, R] = p1.fold(
    haveOutput = (p, c, o) => HaveOutput(noInput(p), c, o)
    , needInput = (_, c) => noInput(c)
    , done = r => Done(r)
    , leftover = (p, _) => noInput(p)
    , pipeM = (mp, c) => PipeM(F.map(mp)(p => noInput(p)), c)
    )

  /**
   * Run a complete pipeline until processing completes.
   */
  def runPipe[F[_], R](p: => Pipe[Void, Void, F, R])(implicit F: Monad[F]): F[R] = p.fold(
    haveOutput = (_, c, _) => runFinalize(c)
    , needInput = (_, c) => runPipe(c)
    , done = r => F.point(r)
    , leftover = (p, _) => runPipe(p)
    , pipeM = (mp, _) => F.bind(mp)(p1 => runPipe(p1))
  )

  /**
   * Send a single output value downstream.
   */
  def yieldp[A, B, F[_]](b: => B)(implicit F: Monad[F]): Pipe[A, B, F, Unit] =
    HaveOutput(Done(None, ()), FinalizePure(()), b)

  /**
   * yieldBind is equivalent to yield b flatMap(_ => p), but the implementation is more efficient.
   */
  def yieldBind[A, B, F[_]](o: => B, p: => Pipe[A, B, F, Unit])(implicit F: Monad[F]): Pipe[A, B, F, Unit] =
     HaveOutput(p, p.pipeClose, o)

  /**
   * equivalent to yield mapM_ yield, but is more efficient.
   */
  def yieldMany[A, B, F[_]](os: => Stream[B])(implicit F: Monad[F]): Pipe[A, B, F, Unit] = {
    def go(s: => Stream[B]): Pipe[A, B, F, Unit] = s match {
      case Stream.Empty => Done(())
      case x #:: xs => HaveOutput(go(xs), FinalizePure(()), x)
    }
    go(os)
  }

  /**
   * Wait for a single input value from upstream, and remove it from the
   * stream. Returns [[scala.None]] if no more data is available.
   */
  def await[F[_], A, B](implicit F: Monad[F]): Pipe[A, B, F, Option[A]] =
    NeedInput(i => Done(Some(i)), Done(None))


  def leftover[F[_], A, B](i: => A): Pipe[A, B, F, Unit] = Leftover(Done(()), i)

  /**
   * Wait for a single input value from upstream, and remove it from the
   * stream. Returns [[scala.None]] if no more data is available.
   */
  def hasInput[A, B, F[_]](implicit F: Monad[F]): Pipe[A, B, F, Boolean] =
    NeedInput(i => Leftover(Done(true), i), Done(false))

  /**
   * The [[conduits.empty.Void]] type parameter for Sink in the output, makes
   * it difficult to compose it with Sources and Conduits. This function replaces
   * that parameter with a free variable. The function is essentially `id`: it
   * only modifies the types, not the actions performed.
   *
   */
  def sinkToPipe[A, B, F[_], R](s: Sink[A, F, R])(implicit F: Monad[F]): Pipe[A, B, F, R] = s.fold(
    haveOutput = (_, _, o) => Void.absurd(o)
    , needInput = (p, c) => NeedInput(i => sinkToPipe(p.apply(i)), sinkToPipe(c))
    , done = r => Done(r)
    , leftover = (p, i) => Leftover(sinkToPipe(p), i)
    , pipeM = (mp, c) => PipeM(F.map(mp)(p => sinkToPipe(p)), c)
  )

  def bracketP[F[_], A, B](alloc: IO[A], free: A => IO[Unit], inside: A => TPipe[A, B, F, Unit])(implicit MR: MonadResource[F]): Pipe[A, B, F, Unit] = {
    implicit val M = MR.MO
    def start: F[Pipe[A, B, F, Unit]] = {
      M.map(MR.allocate(alloc, free)){case (key, seed) => TPipe.toPipeFinalize[F, A, B, Unit](FinalizeM(MR.release(key)), inside(seed))}
    }
    PipeM(start, FinalizePure(()))
  }
}

object pipes extends PipeInstances with PipeFunctions {

  /**
   * A Pipe that produces a stream of output values, without consuming any input.
   * A Source does not produce a final result, thus the result parameter is [[scala.Unit]].
   */
   type Source[F[_], A] = Pipe[Void, A, F, Unit]

  /**
   * A Pipe that consumes a stream of input values and produces a final result.
   * It cannot produce any output values, and thus the output parameter is [[conduits.pipes.Zero]]
   */
   type Sink[A, F[_], R] = Pipe[A, Void, F, R]

  /**
   * A Pipe that consumes a stream of input values and produces a stream
   * of output values. It does not produce a result value, and thus the result
   * parameter is [[scala.Unit]]
   */
   type Conduit[A, F[_], B] = Pipe[A, B, F, Unit]

}