package resourcet

import scalaz._
import Kleisli._
/**
 * User: arjan
 */
/**
 * The express purpose of this transformer is to allow non-@IO@-based monad
 * stacks to catch exceptions via the 'MonadThrow' typeclass.
 */
case class ExceptionT[F[_], A](value: Kleisli[F, ExceptionT[F, A], Either[Throwable, A]]) {
  def flatMap[B](f: A => ExceptionT[F, B])(implicit M: Monad[F]): ExceptionT[F, B] = {
    ExceptionT[F, B](kleisli(et => M.bind(value.run(this))((ei: Either[Throwable, A]) => ei match {
      case Left(e) => M.point(Left(e))
      case Right(a) => f(a).value.run(et)
    })))
  }
//  /**Same as 'runExceptionT', but immediately 'E.throw' any exception returned.*/
  def runExceptionT_(implicit M: Monad[F]): F[A] = {
    M.map(value.run(this))(e => e match {
      case Left(t) => throw t
      case Right(v) => v
    })
  }
}

trait ExceptionTInstances {
  implicit def exceptionTMonad[F[_]](implicit F0: Monad[F]): Monad[({type l[a] = ExceptionT[F, a]})#l] = new Monad[({type l[a] = ExceptionT[F, a]})#l] {
    def bind[A, B](fa: ExceptionT[F, A])(f: (A) => ExceptionT[F, B]): ExceptionT[F, B] = fa flatMap f

    def point[A](a: => A) = ExceptionT[F, A](kleisli(s => F0.point(Right(a))))
  }
}

object ExceptionT extends ExceptionTInstances

