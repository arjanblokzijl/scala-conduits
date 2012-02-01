package conduits


import scalaz.Monad
import scalaz.effect._
import scalaz.effect.IO._
import collection.immutable.IntMap


trait Resource[F[_]] {
  implicit def F: Monad[F]
  type Ref[A]
  implicit def hasRef: HasRef[F, Ref]
  def resourceLiftBase[A](base: F[A]): F[A]
  def resourceLiftBracket[A](init: F[Unit], cleanup: F[Unit], body: F[A]): F[A]

//  register :: Resource m
//           => Base m ()
//           -> ResourceT m ReleaseKey
//  register rel = ResourceT $ \istate -> resourceLiftBase $ register' istate rel

}

trait HasRef[F[_], G[_]] {
  implicit def F: Monad[F]
  def newRef[A](a: => A) : F[G[A]]
  def readRef[A](ref: => G[A]): F[A]
  def writeRef[A](a: => A)(ref: => G[A]) : F[Unit]
  def atomicModifyRef[A, B](sa: G[A])(f: (A => (A, B))): F[B] = {
    F.bind(readRef(sa))(a0 => {
      val (a, b) = f(a0)
      F.bind(writeRef(a)(sa))(_ => F.point(b))
    })
  }

  def registerRef[A](istate: G[ReleaseMap[F[_]]], rel: F[Unit]): F[ReleaseKey] = {
    atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) => {
      (ReleaseMap(rMap.key + 1, rMap.refCount, rMap.m.updated(rMap.key, rel)), ReleaseKey(rMap.key))
    })
  }
}

case class ReleaseKey(key: Int)

object ReleaseMap {
  def apply[F[_], A](base: F[_]): ReleaseMap[F[_]] = ReleaseMap(0, 0, IntMap((0, base)))
}
case class ReleaseMap[A](key: Int, refCount: Int, m: Map[Int, A] = Map[Int, A]())

trait HasRefInstances {
  implicit val ioHasRef = new HasRef[IO, IORef] {
    implicit def F = IO.ioMonad

    def readRef[A](ref: => IORef[A]): IO[A] = ref.read
    def newRef[A](a: => A): IO[IORef[A]] = IO.newIORef(a)
    def writeRef[A](a: => A)(ref: => IORef[A]) = ref.write(a)
  }

  implicit def stHasRef[S] = new HasRef[({type λ[α] = ST[S, α]})#λ, ({type λ[α] = STRef[S, α]})#λ] {
    implicit def F: Monad[({type λ[α] = ST[S, α]})#λ] = ST.stMonad[S]
    def newRef[A](a: => A) = ST.newVar(a)
    def readRef[A](ref: => STRef[S, A]) = ref.read
    def writeRef[A](a: => A)(ref: => STRef[S, A]) = ref.write(a).map(_ => ())
  }
}

object hasRefs extends HasRefInstances

trait ResourceInstances {
  implicit def ioResource = new Resource[IO] {
    implicit def F = ioMonad
    type Ref[A] = IORef[A]
    implicit def hasRef = hasRefs.ioHasRef
    def resourceLiftBase[A](base: IO[A]) = base
    def resourceLiftBracket[A](init: IO[Unit], cleanup: IO[Unit], body: IO[A]): IO[A] =
      ExceptionControl.bracket(init)(_ => cleanup)(_ => body)
  }

  implicit def stResource[S] = new Resource[({type λ[α] = ST[S, α]})#λ] {
    val stMonad: Monad[({type λ[α] = ST[S, α]})#λ] = ST.stMonad[S] /*type annotation to keep intellij more or less happy*/
    implicit def F = stMonad

    type Ref[A] = STRef[S, A]

    implicit def hasRef = hasRefs.stHasRef[S]
    def resourceLiftBase[A](base: ST[S, A]) = base

    def resourceLiftBracket[A](ma: ST[S, Unit], mb: ST[S, Unit], mc: ST[S, A]) =
      ma.flatMap(_ => mc.flatMap(c => mb.flatMap(_ => stMonad.point(c))))
  }
}
