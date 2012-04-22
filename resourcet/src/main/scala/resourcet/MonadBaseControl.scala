package resourcet

import scalaz.Monad
import scalaz.effect.{MonadControlIO, IO}
import IO._


trait MonadBase[B[_], F[_]] {
  implicit def B: Monad[B]

  implicit def F: Monad[F]

  def liftBase[A](fa: => B[A]): F[A]

  def liftBracket[A](init: B[Unit], cleanup: B[Unit], body: F[A]): F[A]
}

object monadControlIO {
  implicit def ioMonadControlIo = new MonadControlIO[IO] {

    def bind[A, B](fa: IO[A])(f: (A) => IO[B]) = fa flatMap f

    def point[A](a: => A) = ioMonad.point(a)

    def liftControlIO[A](f: (IO.RunInBase[IO, IO]) => IO[A]) = idLiftControl(f)
  }
}