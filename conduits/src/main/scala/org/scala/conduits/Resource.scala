package org.scala.conduits

import scalaz.Monad
import scalaz.effect._
import scalaz.effect.IO._

/**
 * User: arjan
 */

//trait Base[F[_], A] extends Monad[F] {
//  implicit def F: Monad[F]
//}

trait Resource[F[_]] {
  implicit def F: Monad[F]
//  def hasRef[A]: HasRef[Base]
//  implicit def hasRef[A]: HasRef[Base[F, A]]

  def resourceLiftBase[A](base: F[A]): F[A]
  def resourceLiftBracket[A](init: F[Unit], cleanup: F[Unit], body: F[A]): F[A]
}



trait HasRef[F[_]] {
  implicit def F: Monad[F]
  type Ref[F, A]
  def newRef[A](a: => A) : F[Ref[F[_], A]]
  def readRef[A](ref: => Ref[F[_], A]) : F[A]
  def writeRef[A](a: => A)(ref: => Ref[F[_], A]) : F[Unit]
}

trait HasRefInstances {
  implicit val ioHasRef = new HasRef[IO] {
    implicit def F = IO.ioMonad

    type Ref[F, A] = IORef[A]
    def newRef[A](a: => A): IO[IORef[A]] = IO.newIORef(a)
    def readRef[A](ref: => IORef[A]): IO[A] = ref.read
    def writeRef[A](a: => A)(ref: => IORef[A]) = ref.write(a)
  }
}

trait ResourceInstances {
  implicit val ioResource = new Resource[IO] {
    implicit def F = ioMonad

    def resourceLiftBase[A](base: IO[A]) = base

    def resourceLiftBracket[A](init: IO[Unit], cleanup: IO[Unit], body: IO[A]): IO[A] =
      ExceptionControl.bracket(init)(_ => cleanup)(_ => body)
  }
}

