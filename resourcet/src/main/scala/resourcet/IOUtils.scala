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
    mask[C, C](restore => for {
      a <- before
      r <- restore(thing(a)) onException (after(a))
      _ <- after(a)
    } yield (r))
  }

  def bracket_[A, B, C](before: IO[A], after: IO[B], thing: IO[C]): IO[C] =
    bracket[A, B, C](before, _ => after, _ => thing)

  def mask[A, B](action: (IO[A] => IO[A]) => IO[B]): IO[B] = {
    def restore(act: IO[A]): IO[A] = act
    action(restore)
  }

  def withFile[A](f: FileOutputStream)(thing: FileOutputStream => IO[A]): IO[A] = bracket[FileOutputStream, Unit, A](IO(f), f => IO(f.close), thing)

}