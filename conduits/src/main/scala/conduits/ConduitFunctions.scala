package conduits

import scalaz.Monad
import pipes._
import Pipe._
import scalaz.effect.IO
import resourcet.{ReleaseKey, MonadResource}
import Finalize._

/**
* User: arjan
*/
trait ConduitStateResult[S, A, B] {
  def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> S, => Stream[B]) => Z): Z
}

trait ConduitIOResult[A, B] {
  def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> Stream[B]) => Z): Z
}

trait SequencedSinkResponse[S, F[_], A, B] {
  def fold[Z](emit: (=> S, => Stream[B]) => Z, stop: => Z, startConduit: (=> Conduit[A, F, B]) => Z): Z
}

object SequencedSinkResponse {
  import FoldUtils._
  //Set a new state and emit some output
  object Emit {
    def apply[S, F[_], A, B](s: => S, output: => Stream[B]) = new SequencedSinkResponse[S, F, A, B] {
      def fold[Z](emit: (=> S, => Stream[B]) => Z, stop: => Z, startConduit: (=> Conduit[A, F, B]) => Z): Z = emit(s, output)
    }
    def unapply[S, F[_], A, B](s: SequencedSinkResponse[S, F, A, B]): Option[(S, Stream[B])] = s.fold((s, o) => Some(s, o), None, ToNone1)
  }
  //End the Conduit
  object Stop {
    def apply[S, F[_], A, B]() = new SequencedSinkResponse[S, F, A, B] {
      def fold[Z](emit: (=> S, => Stream[B]) => Z, stop: => Z, startConduit: (=> Conduit[A, F, B]) => Z): Z = stop
    }
    def unapply[S, F[_], A, B](s: SequencedSinkResponse[S, F, A, B]): Boolean = s.fold((_,_) => false, true, _ => false)
  }
  //Pass control to a new Conduit.
  object StartConduit {
    def apply[S, F[_], A, B](c: Conduit[A, F, B]) = new SequencedSinkResponse[S, F, A, B] {
      def fold[Z](emit: (=> S, => Stream[B]) => Z, stop: => Z, startConduit: (=> Conduit[A, F, B]) => Z): Z = startConduit(c)
    }
    def unapply[S, F[_], A, B](s: SequencedSinkResponse[S, F, A, B]): Option[Conduit[A, F, B]] = s.fold(ToNone2, None, Some(_))
  }
}

object ConduitFunctions {

  import FoldUtils._
  object StateFinished {
    def apply[S, A, B](maybeInput: => Option[A], output: => Stream[B]) = new ConduitStateResult[S, A, B] {
      def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> S, => Stream[B]) => Z) = finished(maybeInput, output)
    }
    def unapply[S, A, B](r: ConduitStateResult[S, A, B]): Option[(Option[A], Stream[B])] = r.fold((i, o) => Some(i, o), ToNone2)
  }
  object StateProducing {
    def apply[S, A, B](state: => S, output: => Stream[B]) = new ConduitStateResult[S, A, B] {
      def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> S, => Stream[B]) => Z) = producing(state, output)
    }
    def unapply[S, A, B](r: ConduitStateResult[S, A, B]): Option[(S, Stream[B])] = r.fold(ToNone2, (s, o) => Some(s, o))
  }

  object IOFinished {
    def apply[A, B](maybeInput: => Option[A], output: => Stream[B]) = new ConduitIOResult[A, B] {
      def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> Stream[B]) => Z) = finished(maybeInput, output)
    }
    def unapply[A, B](r: ConduitIOResult[A, B]): Option[(Option[A], Stream[B])] = r.fold((i, o) => Some(i, o), ToNone)
  }

  object IOProducing {
    def apply[A, B](output: => Stream[B]) = new ConduitIOResult[A, B] {
      def fold[Z](finished: (=> Option[A], => Stream[B]) => Z, producing: (=> Stream[B]) => Z) = producing(output)
    }
    def unapply[A, B](r: ConduitIOResult[A, B]): Option[(Stream[B])] = r.fold(ToNone2, Some(_))
  }

//  case class ConduitSt[S, A](state: S, input: A, push: )
  /**
   * Construct a 'Conduit' with some stateful functions. This function addresses
   * threading the state value for you.
   */
  def conduitState[S, A, F[_], B](state: => S, push: (=> S, => A) => F[ConduitStateResult[S, A, B]], close: (=> S) => F[Stream[B]])(implicit M: Monad[F]): Conduit[A, F, B] = {
    def push1(s: => S)(input: A): Conduit[A, F, B] =
      PipeM(M.map(push(s, input))(r => goRes(r)), FinalizePure(()))

    def close1(s: S): Pipe[A, B, F, Unit] = PipeM(M.bind(close(s))(os => M.point(fromList(os))), FinalizePure(()))

    def goRes(res: ConduitStateResult[S, A, B]): Conduit[A, F, B] = res.fold(
      finished = (leftover, output) => haveMore[A, F, B](Done(leftover, ()), M.point(()), output)
     , producing = (s, output) => haveMore[A, F, B](NeedInput(push1(s), close1(s)), M.point(()), output)
    )
    NeedInput(push1(state), close1(state))
  }

  /*Construct a Conduit*/
  def conduitIO[F[_], A, B, S](alloc: IO[S], cleanup: S => IO[Unit], push: S => A => F[ConduitIOResult[A, B]], close: S => F[Stream[B]])(implicit M0: MonadResource[F]): Conduit[A, F, B] = {
    implicit val M = M0.MO
    def push1(key: => ReleaseKey, state: => S, input: => A): F[Conduit[A, F, B]] = {
      M.bind(push(state)(input))(res => res.fold(
        finished = (leftover, output) => M.bind(M0.release(key))(_ => M.point(haveMore(Done(leftover, ()), M.point(()), output)))
        ,
        producing = output => M.point(haveMore(
          NeedInput(i => PipeM(push1(key, state, i), FinalizeM(M0.release(key))), close1(key, state))
          , M.bind(M0.release(key))(_ => M.point(()))
          , output
        ))))
    }
    def close1(key: ReleaseKey, state: S): Pipe[A, B, F, Unit] =
      PipeM(M.bind(close(state))(output => M.bind(M0.release(key))(_ => M.point(fromList(output)))), FinalizeM(M0.release(key)))

    NeedInput(input =>
              PipeM(M.bind(M0.allocate(alloc, cleanup))(ks =>
                push1(ks._1, ks._2, input)), FinalizePure(()))
              , PipeM(M.bind(M0.allocate(alloc, cleanup))(ks =>
               M.bind(close(ks._2))(os =>
                 M.bind(M0.release(ks._1))(_ => M.point(fromList(os))))), FinalizePure(())
               ))
  }

  def haveMore[A, F[_], B](res: Conduit[A, F, B], close: F[Unit], bs: Stream[B])(implicit M: Monad[F]): Conduit[A, F, B] = bs match {
    case Stream.Empty => res
    case x #:: xs => HaveOutput(haveMore(res, close, xs), FinalizeM(close), x)
  }

  def fromList[A, F[_], B](bs: Stream[B])(implicit M: Monad[F]): Pipe[A, B, F, Unit] = bs match {
    case Stream.Empty => Done(())
    case x #:: xs => HaveOutput[A, B, F, Unit](fromList(xs), FinalizePure(()), x)
  }

  import SequencedSinkResponse._
  /**
   * Helper type for constructing a Conduit based on Sinks. Allows
   * writing higher level code.
   */
  type SequencedSink[S, A, F[_], B] = S => Sink[A, F, SequencedSinkResponse[S, F, A, B]]

//  -- | Convert a 'SequencedSink' into a 'Conduit'.
  /**
   * Convert a `SequencedSink` into a Conduit.
   */
  def sequenceSink[S, A, F[_], B](state: => S, fsink: SequencedSink[S, A, F, B])(implicit M: Monad[F]): Conduit[A, F, B] = {
    hasInput[A, B, F] flatMap(x =>
      if (x) {
//        println("x is true, state is " + state)
        sinkToPipe(fsink(state)) flatMap(res => res match {
          case Emit(s1, os) => {
//            println("Emit: s1 is %s, os is %s" format(s1, os.force))
            fromList[A, F, B](os).flatMap(_ => sequenceSink(s1, fsink))
          }
          case Stop() => Done(())
          case StartConduit(c) => c
        })
      }
      else Done(())
    )
  }

  def sequence[A, F[_], B](sink: Sink[A, F, B])(implicit M: Monad[F]): Conduit[A, F, B] =
    hasInput[A, B, F] flatMap(x =>
      if (x)
        sinkToPipe(sink) flatMap(b => yieldp[A, B, F](b).flatMap(_ => sequence(sink)))
      else pipeMonad[A, B, F].point(())
    )
}
