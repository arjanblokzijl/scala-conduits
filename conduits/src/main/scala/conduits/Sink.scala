package conduits


import resourcet.resource
import resource._
import scalaz.{MonadTrans, Functor, Monad}
import scalaz.effect.{IO, MonadIO}

/**
 * A Sink is a consumer of data.
 * Basic examples would be a sum function (adding up a stream of numbers fed in), a file sink (which writes all incoming bytes to a file), or a socket.
 * We push data into a sink. When the sink finishes processing, it returns some value.
 * @tparam I the input element type that the sink consumes
 * @tparam F The type constructor representing an effect.
 * @tparam A The output element type a Sink produces.
 */
sealed trait Sink[I, F[_], A] {
  def map[B](f: A => B)(implicit M: Monad[F]): Sink[I, F, B]
  def flatMap[B](f: A => Sink[I, F, B])(implicit M: Monad[F]): Sink[I, F, B]
  def sinkClose(implicit M: Monad[F]): F[A]
}

case class Processing[I, F[_], A](push: sinks.SinkPush[I, F, A], close: sinks.SinkClose[I, F, A]) extends Sink[I, F, A] {
  def map[B](f: A => B)(implicit M: Monad[F]): Sink[I, F, B] = Processing[I, F, B](i =>
    push(i) map f, M.map[A, B](close)(r => f(r)))

  def flatMap[B](f: A => Sink[I, F, B])(implicit M: Monad[F]): Sink[I, F, B] =
    Processing[I, F, B](i => push(i) flatMap f, M.bind(close)((cl: A) => f(cl).sinkClose))

  def sinkClose(implicit M: Monad[F]) = close
}

case class Done[I, F[_], A](input: Option[I], output: A) extends Sink[I, F, A] {
  def map[B](f: A => B)(implicit M: Monad[F]): Sink[I, F, B] = Done[I, F, B](input, f(output))
  def flatMap[B](f: A => Sink[I, F, B])(implicit M: Monad[F]): Sink[I, F, B] = input match {
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

  def sinkClose(implicit M: Monad[F]) = M.point(output)
}
case class SinkM[I, F[_], A](msink: F[Sink[I, F, A]]) extends Sink[I, F, A] {
  def map[B](f: (A) => B)(implicit M: Monad[F]) = SinkM(M.map(msink)(s => s.map(f)))
  def flatMap[B](f: A => Sink[I, F, B])(implicit M: Monad[F]): Sink[I, F, B] = SinkM(M.map(msink)(s => s flatMap f))

  def sinkClose(implicit M: Monad[F]) = M.bind(msink)(s => s sinkClose)
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