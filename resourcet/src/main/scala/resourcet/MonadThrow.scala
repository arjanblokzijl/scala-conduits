package resourcet

import scalaz.Monad
import scalaz.effect.IO

/**
 * User: arjan
 */

trait MonadThrow[F[_]] {
  implicit def M: Monad[F]

  def monadThrow[A](e: Throwable): F[A]
}

trait MonadThrowInstances {
  implicit def monadThrowIO(implicit F0: Monad[IO]): MonadThrow[IO] = new MonadThrow[IO] {
    implicit def M = F0

    def monadThrow[A](e: Throwable) = IO.throwIO[A](e)
  }
}

object MonadThrow extends MonadThrowInstances