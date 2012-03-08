package resourcet

import collection.immutable.IntMap
import scalaz._
import scalaz.effect._
import scalaz.Kleisli._
import scalaz.effect.IO.ioMonad

case class ResourceT[F[_], A](value: Kleisli[F, IORef[ReleaseMap], A])

case class ReleaseKey(key: Int)

sealed trait ReleaseMap

case class ReleaseMapOpen(key: Int, refCount: Int, m: Map[Int, IO[Unit]]) extends ReleaseMap

case object ReleaseMapClosed extends ReleaseMap

trait MonadResource[F[_]] {
  implicit def MO: MonadIO[F]

  def allocate[A](a: IO[A], f: A => IO[Unit]): F[(ReleaseKey, A)]

  def register(a: => IO[Unit]): F[ReleaseKey]

  def release(rk: ReleaseKey): F[Unit]
}

trait ResourceTInstances0 {
  implicit def resourceTMonadIO[F[_]](implicit M0: MonadIO[F], M1: Monad[F]): MonadIO[({type l[a] = ResourceT[F, a]})#l] = new MonadIO[({type l[a] = ResourceT[F, a]})#l] with ResourceTMonad[F] {
    implicit def F: Monad[F] = M1

    def liftIO[A](ioa: IO[A]) = ResourceT(kleisli(_ => M0.liftIO(ioa)))
  }
}

trait ResourceTInstances extends ResourceTInstances0 {

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

  implicit def resourceTMonadBaseIo = new MonadBase[IO, IO] {
    implicit def B: Monad[IO] = ioMonad

    implicit def F: Monad[IO] = ioMonad

    def liftBase[A](fa: => IO[A]) = fa

    def liftBracket[A](init: IO[Unit], cleanup: IO[Unit], body: IO[A]): IO[A] = ExceptionControl.bracket_(init, cleanup, body)
  }



  implicit def resourceTMonadResource[F[_]](implicit F0: MonadIO[F], B0: MonadBase[IO, F]): MonadResource[({type l[a] = ResourceT[F, a]})#l] = new MonadResource[({type l[a] = ResourceT[F, a]})#l] {
    implicit def MO = resourceTMonadIO[F]

    def register(rel: => IO[Unit]) = ResourceT(kleisli(istate => F0.liftIO(resource.register(istate, rel))))

    def release(rk: ReleaseKey) = ResourceT(kleisli(istate => F0.liftIO(resource.release(istate, rk))))

    def allocate[A](acquire: IO[A], rel: (A) => IO[Unit]) = ResourceT(kleisli(istate =>
      F0.liftIO(ExceptionControl.mask[A, (ReleaseKey, A)](restore =>
        ioMonad.bind(restore(acquire))(a => ioMonad.map(resource.register(istate, rel(a)))(key => (key, a)))))))
  }
}

private trait ResourceTMonad[F[_]] extends Monad[({type l[a] = ResourceT[F, a]})#l] {
  implicit def F: Monad[F]

  def bind[A, B](fa: ResourceT[F, A])(f: (A) => ResourceT[F, B]): ResourceT[F, B] =
    ResourceT[F, B](kleisli(s => F.bind(fa.value.run(s))((a: A) => f(a).value.run(s))))

  def point[A](a: => A) = ResourceT[F, A](kleisli(s => F.point(a)))
}


trait ResourceTFunctions {
  def register(istate: IORef[ReleaseMap], rel: IO[Unit]): IO[ReleaseKey] = atomicModifyIORef(istate)((rm: ReleaseMap) => rm match {
    case ReleaseMapOpen(key, rf, m) => (ReleaseMapOpen(key + 1, rf, m.updated(key, rel)), ReleaseKey(key))
    case ReleaseMapClosed => throw new InvalidAccess("register")
  })

  def release(istate: IORef[ReleaseMap], rk: ReleaseKey): IO[Unit] = {
    def lookupAction(rm: ReleaseMap) = rm match {
      case ReleaseMapOpen(next, rf, m) => m.get(rk.key).map(action =>
        (ReleaseMapOpen(next, rf, (m - rk.key)), Some(action))).getOrElse(rm, None)
      case ReleaseMapClosed => throw new InvalidAccess("release")
    }
    ExceptionControl.mask[Unit, Unit](restore => {
      val maction: IO[Option[IO[Unit]]] = atomicModifyIORef(istate)(lookupAction)
      ioMonad.bind(maction)(mf => mf.map(a => restore(a)).getOrElse(ioMonad.point(())))
    })
  }

  def maybe[A, B](b: B, f: A => B, o: Option[A]): B = o.map(x => f(x)).getOrElse(b)


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

  def tryR[A](fa: IO[A]): IO[Either[Throwable, A]] = ioMonad.map(fa)(a => try {
    Right(a)
  } catch {
    case t: Throwable => Left(t)
  })

  def runResourceT[F[_], A](rt: ResourceT[F, A])(implicit B: MonadBase[IO, F]): F[A] = {
    val in: IO[IORef[ReleaseMap]] = IO.newIORef(ReleaseMapOpen(Int.MinValue, Int.MinValue, IntMap((Int.MinValue, ioMonad.point(())))))
    val newRef: F[IORef[ReleaseMap]] = B.liftBase(in)
    B.F.bind(newRef)(istate => {
      B.liftBracket(stateAlloc(istate), stateCleanup(istate), rt.value.run(istate))
    })
  }
}

object resource extends ResourceTFunctions with ResourceTInstances {
  type RTIO[A] = ResourceT[IO, A]
}
