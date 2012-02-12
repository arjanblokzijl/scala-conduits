package conduits

import scalaz.effect._
import scalaz.effect.IO._
import collection.immutable.IntMap
import scalaz.{Kleisli, Monad}
import scalaz.Kleisli._
import scalaz.MonadTrans

trait Resource[F[_]] {
  implicit def F: Monad[F]
  implicit val H: HasRef[F]

  def resourceLiftBase[A](base: F[A]): F[A]
  def resourceLiftBracket[A](init: F[Unit], cleanup: F[Unit], body: F[A]): F[A]
}

trait HasRef[F[_]] {
  implicit def F: Monad[F]

  def newRef[A, G[_]](a: => A)(implicit dep: Dep[F, G]): F[G[A]] = dep.newRef(a)
  def readRef[A, G[_]](ref: => G[A])(implicit dep: Dep[F, G]): F[A] = dep.readRef(ref)
  def writeRef[A, G[_]](a: => A)(ref: => G[A])(implicit dep: Dep[F, G]): F[Unit] = dep.writeRef(a)(ref)

  def atomicModifyRef[A, B, G[_]](sa: G[A])(f: (A => (A, B)))(implicit dep: Dep[F, G]): F[B] = {
    F.bind(readRef(sa))(a0 => {
      val (a, b) = f(a0)
      F.bind(writeRef(a)(sa))(_ => F.point(b))
    })
  }

  def tryR[A](fa: F[A]): F[Either[Throwable, A]] =
    F.map(fa)(a => Right(a))
}

case class ReleaseKey(key: Int)

object ReleaseMap {
  def apply[F[_]](base: F[_]): ReleaseMap[F[_]] = ReleaseMap(Int.MinValue, Int.MinValue, IntMap((Int.MinValue, base)))
}

case class ReleaseMap[A](key: Int, refCount: Int, m: Map[Int, A] = Map[Int, A]())

trait Dep[F[_], G[_]] {
  def newRef[A](a: => A): F[G[A]]
  def readRef[A](ref: => G[A]): F[A]
  def writeRef[A](a: => A)(ref: G[A]): F[Unit]
}

trait HasRefInstances {
  implicit object ioDep extends Dep[IO, IORef] {
    def newRef[A](a: => A): IO[IORef[A]] = IO.newIORef(a)
    def readRef[A](ref: => IORef[A]): IO[A] = ref.read
    def writeRef[A](a: => A)(ref: IORef[A]): IO[Unit] = ref.write(a)
  }

  implicit def stDep[S]: Dep[({type λ[α] = ST[S, α]})#λ, ({type λ[α] = STRef[S, α]})#λ] = new Dep[({type λ[α] = ST[S, α]})#λ, ({type λ[α] = STRef[S, α]})#λ] {
    def newRef[A](a: => A) = ST.newVar(a)
    def readRef[A](ref: => STRef[S, A]): ST[S, A] = ref.read
    def writeRef[A](a: => A)(ref: STRef[S, A]) = ref.write(a).map(_ => ())
  }

  implicit def ioHasRef = new HasRef[IO] {
    implicit def F = IO.ioMonad
  }
  implicit def stHasRef[S] = new HasRef[({type λ[α] = ST[S, α]})#λ] {
    implicit def F = ST.stMonad[S]
  }
}

object hasRefs extends HasRefInstances

trait ResourceInstances {
  implicit def ioResource = new Resource[IO] {
    implicit def F = ioMonad
    implicit val H: HasRef[IO] = hasRefs.ioHasRef

    def resourceLiftBase[A](base: IO[A]) = base

    def resourceLiftBracket[A](init: IO[Unit], cleanup: IO[Unit], body: IO[A]): IO[A] =
      ExceptionControl.bracket(init)(_ => cleanup)(_ => body)
  }

  implicit def stResource[S] = new Resource[({type λ[α] = ST[S, α]})#λ] {
    val stMonad: Monad[({type λ[α] = ST[S, α]})#λ] = ST.stMonad[S] /*type annotation to keep intellij more or less happy*/
    implicit def F = stMonad

    implicit val H: HasRef[({type λ[α] = ST[S, α]})#λ] = hasRefs.stHasRef[S]

    def resourceLiftBase[A](base: ST[S, A]) = base

    def resourceLiftBracket[A](ma: ST[S, Unit], mb: ST[S, Unit], mc: ST[S, A]) =
      ma.flatMap(_ => mc.flatMap(c => mb.flatMap(_ => stMonad.point(c))))
  }

  implicit def resourceTMonad[F[_]](implicit F0: Monad[F]): Monad[({type l[a] = ResourceT[F, a]})#l] = new Monad[({type l[a] = ResourceT[F, a]})#l] {
    def bind[A, B](fa: ResourceT[F, A])(f: (A) => ResourceT[F, B]): ResourceT[F, B] = new ResourceT[F, B] {
      def value[G[_]](implicit D: Dep[F, G]) = kleisli(s => F0.bind(fa.value.run(s))((a: A) => f(a).value.run(s)))
    }

    def point[A](a: => A) = new ResourceT[F, A] {
      def value[G[_]](implicit D: Dep[F, G]) = kleisli(s => F0.point(a))
    }
  }

  implicit def resourceTMonadT[F[_]](implicit F0: Monad[F]): MonadTrans[({type l[a[_], b] = ResourceT[a, b]})#l] = new MonadTrans[({type l[a[_], b] = ResourceT[a, b]})#l] {
    implicit def apply[G[_]](implicit M: Monad[G]): Monad[({type λ[α] = ResourceT[G, α]})#λ] = resourceTMonad[G]
    def liftM[G[_], A](ga: G[A])(implicit M: Monad[G]): ResourceT[G, A] = new ResourceT[G, A] {
      def value[R[_]](implicit D: Dep[G, R]): Kleisli[G, R[ReleaseMap[G[_]]], A] = kleisli(s => M.map(ga)(identity))
    }
  }
}

//  newtype ResourceT m a =
//      ResourceT (Ref (Base m) (ReleaseMap (Base m)) -> m a)
trait ResourceT[F[_], A] {self =>
//  implicit val F: Monad[F]
  def value[R[_]](implicit D: Dep[F, R]): Kleisli[F, R[ReleaseMap[F[_]]], A]
  def apply[R[_]](istate: R[ReleaseMap[F[_]]])(implicit D: Dep[F, R]): F[A] = value.run(istate)
}

trait ResourceFunctions {
  def newRef[F[_], A](a: => A)(implicit R: Resource[F]): ResourceT[F, A] = {
    sys.error("todo")
  }

  def register[F[_], G[_]](rel: F[Unit])(implicit R: Resource[F], D: Dep[F, G], H: HasRef[F]): ResourceT[F, ReleaseKey] =
    new ResourceT[F, ReleaseKey] {
      implicit val F: Monad[F] = H.F
      def value[G[_]](implicit D: Dep[F, G]): Kleisli[F, G[ReleaseMap[F[_]]], ReleaseKey] =
        kleisli(istate => R.resourceLiftBase(registerRef(istate, rel)))
    }

  def registerRef[F[_], G[_]](istate: G[ReleaseMap[F[_]]], rel: F[Unit])(implicit D: Dep[F, G], H: HasRef[F]): F[ReleaseKey] = {
    H.atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) =>
      (ReleaseMap(rMap.key + 1, rMap.refCount, rMap.m.updated(rMap.key, rel)), ReleaseKey(rMap.key))
    )
  }

  def stateAlloc[F[_], G[_]](istate: G[ReleaseMap[F[_]]])(implicit D: Dep[F, G], H: HasRef[F]): F[Unit] =
    H.atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) =>
      (ReleaseMap(rMap.key, rMap.refCount + 1, rMap.m), ()))

  def stateCleanup[F[_], G[_], A](istate: G[ReleaseMap[F[_]]])(implicit D: Dep[F, G], H: HasRef[F]): F[Unit] = {
    val rmap = H.atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) =>
      (ReleaseMap(rMap.key, rMap.refCount - 1, rMap.m), (rMap.refCount - 1, rMap.m)))
    H.F.bind(rmap) {
      case (rf, m) => {
        if (rf == Int.MinValue)
          m.values.toList.foldRight(H.F.point(()))((f, b) => H.F.point(()))
        else H.F.point(())
      }
    }
  }

  def runResourceT[F[_], G[_], A](rt: ResourceT[F, A])(implicit R: Resource[F], D: Dep[F, G], H: HasRef[F]): F[A] =
    R.F.bind(R.resourceLiftBase(H.newRef(ReleaseMap[F[_]](Int.MinValue, Int.MinValue))))(istate => {
      R.resourceLiftBracket(stateAlloc(istate), stateCleanup(istate), rt.apply(istate))
    })
}

object resource extends ResourceFunctions with ResourceInstances