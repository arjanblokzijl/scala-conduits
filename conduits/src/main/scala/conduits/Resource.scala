package conduits

import scalaz.effect._
import scalaz.std.list._
import scalaz.effect.IO._
import collection.immutable.{List, IntMap}
import scalaz.{Kleisli, Monad}
import scalaz.Kleisli._

trait Resource[F[_]] {
  implicit def F: Monad[F]
  implicit val H: HasRef[F]

  def resourceLiftBase[A](base: F[A]): F[A]
  def resourceLiftBracket[A](init: F[Unit], cleanup: F[Unit], body: F[A]): F[A]
}

trait HasRef[F[_]] {
  implicit def F: Monad[F]
  type Ref[F, A]

  def newRef[A](a: => A): F[Ref[F[_], A]]
  def readRef[A](ref: => Ref[F[_], A]): F[A]
  def writeRef[A](a: => A)(ref: => Ref[F[_], A]): F[Unit]

  def atomicModifyRef[A, B](sa: Ref[F[_], A])(f: (A => (A, B))): F[B] = {
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

trait HasRefInstances {
  implicit def ioHasRef = new HasRef[IO] {
    type Ref[F, A] = IORef[A]

    implicit def F = IO.ioMonad

    def newRef[A](a: => A) = IO.newIORef(a)
    def readRef[A](ref: => IORef[A]): IO[A] = ref.read
    def writeRef[A](a: => A)(ref: => IORef[A]) = ref.write(a)
  }

  implicit def stHasRef[S] = new HasRef[({type λ[α] = ST[S, α]})#λ] {
    implicit def F: Monad[({type λ[α] = ST[S, α]})#λ] = ST.stMonad[S]
    type Ref[F, A] = STRef[S, A]

    def newRef[A](a: => A) = ST.newVar(a)
    def readRef[A](ref: => STRef[S, A]) = ref.read
    def writeRef[A](a: => A)(ref: => STRef[S, A]) = ref.write(a).map(_ => ())
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

//  implicit def resourceTMonad[F[_]](implicit H0: HasRef[F], F: Monad[F]): Monad[({type l[a] = ResourceT[F, a]})#l] = new Monad[({type l[a] = ResourceT[F, a]})#l] {
//    def bind[A, B](fa: ResourceT[F, A])(f: (A) => ResourceT[F, B]): ResourceT[F, B] = new ResourceT[F, B] {
//      implicit val H: HasRef[F] = fa.H
//      def value = kleisli(s => F0.bind(fa.value.run(s))((a: A) => f(a).value.run(s)))
//    }
//
//    def point[A](a: => A) = new ResourceT[F, A] {
//      implicit val H: HasRef[F] = H0
//      def value = kleisli(s => F0.point(a))
//    }
//  }

}

//  newtype ResourceT m a =
//      ResourceT (Ref (Base m) (ReleaseMap (Base m)) -> m a)

trait ResourceT[F[_], A] {self =>
  implicit val H: HasRef[F]
  val F0 = H.F

  def value: Kleisli[F, H.Ref[F[_], ReleaseMap[F[_]]], A]
  def apply(istate: H.Ref[F[_], ReleaseMap[F[_]]]): F[A] = value.run(istate)
}

trait ResourceFunctions {
  def newRef[F[_], A](a: => A)(implicit R: Resource[F]): ResourceT[F, A] = {
    sys.error("todo")
  }

  def register[F[_]](rel: F[Unit])(implicit R: Resource[F], H0: HasRef[F]): ResourceT[F, ReleaseKey] =
    new ResourceT[F, ReleaseKey] {
      implicit val H: HasRef[F] = H0
      def value = kleisli(istate =>
        R.resourceLiftBase(registerRef(H)(istate, rel)))
    }

  def registerRef[F[_]](/*implicit*/ H: HasRef[F])(istate: H.Ref[F[_], ReleaseMap[F[_]]], rel: F[Unit]): F[ReleaseKey] = {
    H.atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) =>
      (ReleaseMap(rMap.key + 1, rMap.refCount, rMap.m.updated(rMap.key, rel)), ReleaseKey(rMap.key))
    )
  }

  def stateAlloc[F[_]](/*implicit*/ H: HasRef[F])(istate: H.Ref[F[_], ReleaseMap[F[_]]]): F[Unit] =
    H.atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) =>
      (ReleaseMap(rMap.key, rMap.refCount + 1, rMap.m), ()))

  def stateCleanup[F[_], A](/*implicit*/ H: HasRef[F])(istate: H.Ref[F[_], ReleaseMap[F[_]]]): F[Unit] = {
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

  def runResourceT[F[_], A](rt: ResourceT[F, A])(implicit R: Resource[F]): F[A] =
    R.F.bind(R.resourceLiftBase(rt.H.newRef(ReleaseMap[F[_]](Int.MinValue, Int.MinValue))))(istate => {
      R.resourceLiftBracket(stateAlloc(rt.H)(istate), stateCleanup(rt.H)(istate), rt.apply(istate))
    })
}

object resource extends ResourceFunctions with ResourceInstances