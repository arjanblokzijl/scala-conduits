package conduits

import scalaz.effect.IO
import IO.ioMonad

/**
 * User: arjan
 */

object ExceptionControl {

  def bracket[A, B, C](before: IO[A], after: A => IO[B], thing: A => IO[C]): IO[C] = {
    mask[C, C](restore => {
      ioMonad.bind(before)(a =>
          ioMonad.bind(restore(onException(thing(a))(after(a))))(r =>
              ioMonad.bind(after(a))(_ => ioMonad.point(r))
          )
        )
    })
  }

  def bracket_[A, B, C](before: IO[A], after: IO[B], thing: IO[C]): IO[C] =
     bracket[A, B, C](before, _ => after, _ => thing)


  def mask[A, B](action: (IO[A] => IO[A]) => IO[B]): IO[B] = {
    def restore(act: IO[A]): IO[A] = act
    action(restore)
  }

  def onException[A, B](io: => IO[A])(what: => IO[B]): IO[A] =
    try {io}
    catch {
      case e: Throwable => what.flatMap(_ => throw new Exception(e))
    }
}