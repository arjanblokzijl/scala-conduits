package conduits

import scalaz.effect.{IORef, IO}
import scalaz.Monad


/**
 * User: arjan
 */

trait MonadBaseDep[B[_], F[_]] {
  def liftBase[A](a: => B[A]): F[A]
}

trait MonadBaseControl[B[_], F[_]] {
  implicit def MB: MonadBase[B, F]
  implicit def B: Monad[B] = MB.B
  implicit def F: Monad[F] = MB.F
  type RunInBase[A] = F[A] => B[F[A]]
  def liftBaseWith[A](f: (F[A] => B[A])=> B[A])(implicit D: MonadBaseDep[B, F]): F[A]
//  def liftBaseWith[A](f: F[A] => B[A])(implicit D: MonadBaseDep[B, F]): F[A]
//  data StM m ∷ * → *
  //  type RunInBase m b = ∀ α. m α → b (StM m α)
  //  liftBaseWith ∷ (RunInBase m b → b α) → m α
  //def liftBracket[A](init: F[Unit], cleanup: F[Unit], body: F[A])(implicit D: MonadBaseDep[F, G]): F[A]
  def restoreM[A](a: => A): F[A]
//  restoreM ∷ StM m α → m α
}

trait MonadBase[B[_], F[_]] {
  implicit def B: Monad[B]
  implicit def F: Monad[F]
//  def liftBase[A](fa: => B[A]): F[A]
//  def liftBase[A](fa: => B[A])(implicit D: MonadBaseDep[B, F]): F[A]
  def liftBase[A](fa: => B[A])(implicit D: MonadBaseDep[B, F]): F[A] = D.liftBase(fa)
}

//liftBaseWith ∷ (RunInBase m b → b α) → m α
//class MonadBase b m ⇒ MonadBaseControl b m | m → b where

trait DepInstances {
//  implicit object ioDep extends MonadBaseDep[IO, IORef] {
//    def liftBase[A](a: => IO[A]): IORef[A] = IO.newIORef(a)
//    def liftBracket[A](init: IO[Unit], cleanup: IO[Unit], body: IO[A]) = ExceptionControl.bracket(init, _ => cleanup, _ => body)
//  }
//
//  implicit def ioBaseControl = new MonadBaseControl[IO]{
//    implicit def M = IO.ioMonad
//  }
}

object deps extends DepInstances