package conduits


import resourcet.resource
import resource._
import scalaz.{MonadTrans, Functor, Monad}
import scalaz.effect.{IO, MonadIO}
import Sink._
import sinks.{SinkPush, SinkClose}
/**
 * A Sink is a consumer of data.
 * Basic examples would be a sum function (adding up a stream of numbers fed in), a file sink (which writes all incoming bytes to a file), or a socket.
 * We push data into a sink. When the sink finishes processing, it returns some value.
 * @tparam I the input element type that the sink consumes
 * @tparam F The type constructor representing an effect.
 * @tparam A The output element type a Sink produces.
 */
sealed trait Sink[I, F[_], A] {

  def fold[Z](processing: (=> SinkPush[I, F, A], => SinkClose[I, F, A]) => Z
              , done: (=>  Option[I], => A) => Z
              , sinkM: (=> F[Sink[I, F, A]]) => Z): Z

  def map[B](f: A => B)(implicit M: Monad[F]): Sink[I, F, B] = fold(
    processing = (push, close) => Processing[I, F, B](i => push.apply(i) map f, M.map[A, B](close)(r => f(r)))
    , done = (input, output) => Sink.Done(input, f(output))
    , sinkM = msink => SinkM(M.map(msink)(s => s.map(f)))
  )

  def flatMap[B](f: A => Sink[I, F, B])(implicit M: Monad[F]): Sink[I, F, B] = fold(
    processing = (push, close) => Processing[I, F, B](i => push.apply(i) flatMap f, M.bind(close)((cl: A) => f(cl).sinkClose))
    , done = (input, output) => input match {
        case None => f(output)
        case Some(leftover) => {
          def sinkPush(s: Sink[I, F, B]): Sink[I, F, B] = s match {
            case Processing(push, _) => push(leftover)
            case Done(None, o) => Done(Some(leftover), o)
            case Done(Some(x), _) => sys.error("Sink invariant violated: leftover input returned without any push")
            case SinkM(msink) => SinkM(M.map(msink)(s => sinkPush(s)))
          }
          sinkPush(f(output))
        }
      }
    , sinkM = msink =>  SinkM(M.map(msink)(s => s flatMap f))
  )
  def sinkClose(implicit M: Monad[F]): F[A] = fold(
    processing = (push, close) => close
    , done = (input, output) =>  M.point(output)
    , sinkM = msink => M.bind(msink)(s => s sinkClose)
  )
}

object Sink {
  import Folds._
  object Processing {
    def apply[I, F[_], A](push: =>  sinks.SinkPush[I, F, A], close: => sinks.SinkClose[I, F, A]) = new Sink[I, F, A] {
      def fold[Z](processing: (=> I => Sink[I, F, A], => sinks.SinkClose[I, F, A]) => Z, done: (=> Option[I], => A) => Z, sinkM: (=> F[Sink[I, F, A]]) => Z) =
        processing(push, close)
    }
    def unapply[I, F[_], A](s: Sink[I, F, A]): Option[(SinkPush[I, F, A], SinkClose[I, F, A])] = {
      s.fold((p, c) => Some(p, c), ToNone2, ToNone)
    }
  }
  object Done {
    def apply[I, F[_], A](input: =>  Option[I], output: => A) = new Sink[I, F, A] {
      def fold[Z](processing: (=> I => Sink[I, F, A], => sinks.SinkClose[I, F, A]) => Z, done: (=> Option[I], => A) => Z, sinkM: (=> F[Sink[I, F, A]]) => Z) =
        done(input, output)
    }
    def unapply[I, F[_], A](s: Sink[I, F, A]): Option[(Option[I], A)] = {
      s.fold(ToNone2, (i, o) => Some(i, o), ToNone)
    }
  }
  object SinkM {
    def apply[I, F[_], A](msink: => F[Sink[I, F, A]]) = new Sink[I, F, A] {
      def fold[Z](processing: (=> I => Sink[I, F, A], => sinks.SinkClose[I, F, A]) => Z, done: (=> Option[I], => A) => Z, sinkM: (=> F[Sink[I, F, A]]) => Z) =
        sinkM(msink)
    }
    def unapply[I, F[_], A](s: Sink[I, F, A]): Option[F[Sink[I, F, A]]] = {
      s.fold(ToNone2, ToNone2, s => Some(s))
    }
  }
}


trait SinkInstances {

  implicit def sinkFunctor[I, F[_]](implicit M: Monad[F]): Functor[({type l[a] = Sink[I, F, a]})#l] = new Functor[({type l[a] = Sink[I, F, a]})#l] {
    def map[A, B](fa: Sink[I, F, A])(f: (A) => B): Sink[I, F, B] = fa map f
  }

  implicit def sinkMonad[I, F[_]](implicit M0: Monad[F]): Monad[({type l[a] = Sink[I, F, a]})#l] = new SinkMonad[I, F] {
    implicit val M = M0
  }

  implicit def sinkMonadTrans[I, F[_]](implicit M0: Monad[F]): MonadTrans[({type l[a[_], b] = Sink[I, a, b]})#l] = new MonadTrans[({type l[a[_], b] = Sink[I, a, b]})#l] {
    implicit def apply[M[_]](implicit M0: Monad[M]): Monad[({type l[a] = Sink[I, M, a]})#l] = new SinkMonad[I, M] {
      implicit val M = M0
    }

    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): Sink[I, G, A] = {
      SinkM[I, G, A](M.map(ga)((a: A) => Done(None, a))) //TODO check whether this makes sense
    }
  }

  implicit def sinkMonadIO[I, F[_]](implicit M0: MonadIO[F]): MonadIO[({type l[a] = Sink[I, F, a]})#l] = new SinkMonadIO[I, F] {
    implicit val F = M0
    implicit val M = M0
  }
}

private[conduits] trait SinkMonad[I, F[_]] extends Monad[({type l[a] = Sink[I, F, a]})#l] {
  implicit def M: Monad[F]

  def point[A](a: => A) = Done(None, a)

  def bind[A, B](fa: Sink[I, F, A])(f: (A) => Sink[I, F, B]) = fa flatMap f
}

private[conduits] trait SinkMonadIO[I, F[_]] extends MonadIO[({type l[a] = Sink[I, F, a]})#l] with SinkMonad[I, F] {
  implicit val smt = sinks.sinkMonadTrans[I, F]

  implicit def F: MonadIO[F]

  def liftIO[A](ioa: IO[A]) = MonadTrans[({type l[a[_], b] = Sink[I, a, b]})#l].liftM(F.liftIO(ioa))
}


trait SinkFunctions {
  type SinkPush[I, F[_], A] = I => Sink[I, F, A]
  type SinkClose[I, F[_], A] = F[A]
}

object sinks extends SinkFunctions with SinkInstances