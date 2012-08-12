package conduits
package http

import scalaz.effect.IO

sealed trait ConnError
case object ErrorReset extends ConnError
case object ErrorClosed extends ConnError
case class ErrorParse(msg: String) extends ConnError
case class ErrorMisc(msg: String) extends ConnError

object HttpStream {

  type Result[A] = Either[ConnError, A]

  def failParse[A](msg: String): Result[A] = failWith(ErrorParse(msg))

  def failWith[A](err: ConnError): Result[A] = Left(err)

  def bindE[A, B](a: Result[A])(f: A => Result[B]): Result[B] = a match {
    case Left(e) => Left(e)
    case Right(v) => f(v)
  }

  def mapE[A, B](a: IO[Result[A]])(f: A => Result[B]): IO[Result[B]] =
    a.map(x => x match {
      case Left(e) => Left(e)
      case Right(r) => f(r)
    })

}
