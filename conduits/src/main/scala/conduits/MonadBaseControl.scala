package conduits

import scalaz.effect.{IORef, IO}
import scalaz.Monad


/**
 * User: arjan
 */

trait MonadBase[B[_], F[_]] {
  implicit def B: Monad[B]
  implicit def F: Monad[F]
  def liftBase[A](fa: => B[A]): F[A]
  def liftBracket[A](init: B[Unit], cleanup: B[Unit], body: F[A]): F[A]
}
