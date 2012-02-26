package conduits

import collection.immutable.IntMap
import scalaz.Kleisli._
import scalaz.effect.IO.ioMonad
import scalaz.{DList, MonadTrans, Monad, Kleisli}
import scalaz.effect.{MonadIO, ST, IO, IORef}

case class ResourceT[F[_], A](value: Kleisli[F, IORef[ReleaseMap], A])

case class ReleaseKey(key: Int)

sealed trait ReleaseMap
case class ReleaseMapOpen(key: Int, refCount: Int, m: Map[Int, IO[Unit]]) extends ReleaseMap
case object ReleaseMapClosed extends ReleaseMap

trait ResourceTInstances {

  implicit def resourceTMonad[F[_]](implicit F0: Monad[F]): Monad[({type l[a] = ResourceT[F, a]})#l] = new Monad[({type l[a] = ResourceT[F, a]})#l] {
    def bind[A, B](fa: ResourceT[F, A])(f: (A) => ResourceT[F, B]): ResourceT[F, B] =
      ResourceT[F, B](kleisli(s => F0.bind(fa.value.run(s))((a: A) => f(a).value.run(s))))

    def point[A](a: => A) = ResourceT[F, A](kleisli(s => F0.point(a)))
  }

  implicit def resourceTMonadTrans: MonadTrans[({type l[a[_], b] = ResourceT[a, b]})#l] = new MonadTrans[({type l[a[_], b] = ResourceT[a, b]})#l] {
    implicit def apply[G[_]](implicit M: Monad[G]): Monad[({type λ[α] = ResourceT[G, α]})#λ] = resourceTMonad[G]
    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): ResourceT[G, A] =
      ResourceT[G, A](kleisli(s => M.map(ga)(identity)))
  }

  implicit def resourceMonadBase[B[_], F[_]](implicit B0: Monad[B], F0: Monad[F]): MonadBase[B, ({type l[a] = ResourceT[F, a]})#l] = new MonadBase[B, ({type l[a] = ResourceT[F, a]})#l] {
    implicit def B = B0
    implicit def F = resourceTMonad[F]
  }

  implicit def resourceTBaseMonadIoDep[F[_]](implicit F: MonadIO[F]): MonadBaseDep[IO, ({type l[a] = ResourceT[F, a]})#l] = new MonadBaseDep[IO, ({type l[a] = ResourceT[F, a]})#l] {
    implicit val B0 = ioMonad
    def liftBase[A](a: => IO[A]) = MonadTrans[({type l[a[_], b] = ResourceT[a, b]})#l].liftM(F.liftIO(a))
  }
}

trait ResourceBaseControl[F[_]] {
  implicit def B: MonadBaseControl[IO, F]
  implicit def M: MonadIO[F]
}

trait ResourceTFunctions {
  def register[F[_], G[_]](istate: IORef[ReleaseMap], rel: IO[Unit]): IO[ReleaseKey] = atomicModifyIORef(istate)((rm: ReleaseMap) => rm match {
    case ReleaseMapOpen(key, rf, m) => (ReleaseMapOpen(key + 1, rf, m.updated(key, rel)), ReleaseKey(key))
    case ReleaseMapClosed => throw new InvalidAccess("register")
  })

  class InvalidAccess(name: String) extends RuntimeException("%s: The mutable state is being accessed after cleanup. Please contact the maintainers." format name)

  def atomicModifyIORef[A, B, G[_]](ref: IORef[A])(f: (A => (A, B))): IO[B] = {
    ioMonad.bind(ref.read)(a0 => {
      val (a, b) = f(a0)
      ioMonad.bind(ref.write(a))(_ => ioMonad.point(b))
    })
  }

  def stateAlloc[F[_]](istate: IORef[ReleaseMap]): IO[Unit] =
    atomicModifyIORef(istate)((rm: ReleaseMap) => rm match {
      case ReleaseMapOpen(nk, rf, m) => (ReleaseMapOpen(nk, rf + 1, m), ())
      case ReleaseMapClosed => throw new InvalidAccess("stateAlloc")
    })

  def stateCleanup[F[_], A](istate: IORef[ReleaseMap]): IO[Unit] = {
    val io: IO[Option[Map[Int, IO[Unit]]]] = atomicModifyIORef(istate)((rm: ReleaseMap) => rm match {
      case ReleaseMapOpen(nk, rf, m) => {
        val rf1 = rf - 1
        if (rf1 == Int.MinValue) (ReleaseMapClosed, Some(m))
        else (ReleaseMapOpen(nk, rf1, m), None)
      }
      case ReleaseMapClosed => throw new InvalidAccess("stateAlloc")
    })
    ioMonad.bind(io)((mm: Option[Map[Int, IO[Unit]]]) => mm.map(m => {
      val dl = DList.fromList(m.values.toList)
      dl.foldr[IO[Unit]](ioMonad.point(()))((x, b) => ioMonad.bind(tryR(x))(_ => ioMonad.point(())))
    }).getOrElse(ioMonad.point(())))
  }

  def tryR[A](fa: IO[A]): IO[Either[Throwable, A]] = ioMonad.map(fa)(a => try {Right(a)} catch {case t: Throwable => Left(t)})

  def runResourceT[F[_], A](rt: ResourceT[F, A])(implicit B: MonadBaseControl[IO, F], D: MonadBaseDep[IO, F]): F[A] = {
    val in: IO[IORef[ReleaseMap]] = IO.newIORef(ReleaseMapOpen(Int.MinValue, Int.MinValue, IntMap((Int.MinValue, ioMonad.point(())))))
    val newRef: F[IORef[ReleaseMap]] = B.MB.liftBase(in)
    B.F.bind(newRef)(istate => {
      bracket_(stateAlloc(istate), stateCleanup(istate), rt.value.run(istate))
    })
  }

  def bracket_[F[_], A](alloc: IO[Unit], cleanup: IO[Unit], inside: F[A])(implicit B: MonadBaseControl[IO, F], D: MonadBaseDep[IO, F]): F[A] =
    control[IO, F, A](run =>
      ExceptionControl.bracket_(alloc, cleanup, run(inside)))


  def control[B[_], F[_], A](f: (F[A] => B[A]) => B[A])(implicit B: MonadBaseControl[B, F], D: MonadBaseDep[B, F]): F[A] =
    B.F.bind(B.liftBaseWith(f))(istate => B.restoreM(istate))
}

object resource extends ResourceTFunctions with ResourceTInstances
