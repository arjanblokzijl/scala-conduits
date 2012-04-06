package resourcet

import scalaz.effect.IO
import IO.ioMonad
import java.io.{FileOutputStream, File}
import java.nio.channels.ByteChannel

/**
 * User: arjan
 */

object IOUtils {

  def bracket[A, B, C](before: => IO[A], after: A => IO[B], thing: A => IO[C]): IO[C] = {
    mask[C, C](restore => {
      ioMonad.bind(before)(a =>
        ioMonad.bind(restore(thing(a) onException (after(a))))(r =>
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

  def withChannel[A](c: ByteChannel)(thing: ByteChannel => IO[A]): IO[A] = bracket[ByteChannel, Unit, A](IO(c), c => IO(c.close), thing)

}