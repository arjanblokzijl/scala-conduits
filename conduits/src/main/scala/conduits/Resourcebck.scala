//package conduits
//
//import scalaz.Monad
//import scalaz.effect._
//import scalaz.effect.IO._
//import collection.immutable.IntMap
//
////trait Base[F[_], A] {
////  implicit def F: Monad[F]
////  implicit def HasRef[F]
//////  def value: F[A]
////}
//
//trait Resource[F[_]] {
//  implicit def F: Monad[F]
//  implicit val hasRef: HasRef[F]
//  def resourceLiftBase[A](base: F[A]): F[A]
//  def resourceLiftBracket[A](init: F[Unit], cleanup: F[Unit], body: F[A]): F[A]
//
////  register :: Resource m
////           => Base m ()
////           -> ResourceT m ReleaseKey
////  register rel = ResourceT $ \istate -> resourceLiftBase $ register' istate rel
//
//  //  register' :: HasRef base
//  //            => Ref base (ReleaseMap base)
//  //            -> base ()
//  //            -> base ReleaseKey
//  //  register' istate rel = atomicModifyRef' istate $ \(ReleaseMap key rf m) ->
//  //      ( ReleaseMap (key + 1) rf (IntMap.insert key rel m)
//  //      , ReleaseKey key
//  //      )
////  def registerRef[A](istate: HasRef[F]#Ref[F[_], ReleaseMap[F[_]]], rel: F[Unit])(implicit H: HasRef[F]): F[ReleaseKey] = {
////    hasRef.atomicModifyRef[ReleaseMap[F[_]], ReleaseKey](istate)((rMap: ReleaseMap[F[_]]) => {
////      (ReleaseMap(rMap.key + 1, rMap.refCount, rMap.m.updated(rMap.key, rel)), ReleaseKey(rMap.key))
////    })
////  }
//  def registerRef[A](istate: hasRef.Ref[F[_], ReleaseMap[F[_]]], rel: F[Unit]): F[ReleaseKey] = {
//    hasRef.atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) => {
//      (ReleaseMap(rMap.key + 1, rMap.refCount, rMap.m.updated(rMap.key, rel)), ReleaseKey(rMap.key))
//    })
//  }
//
//}
//object resources {
//  type Ref[F[_], A] = F[A]
//}
//
//import resources._
//
//trait HasRef[F[_]] {
//  implicit def F: Monad[F]
//  type Ref[F, A]
//  def newRef[A](a: => A) : F[Ref[F[_], A]]
//  def readRef[A](ref: => Ref[F[_], A]) : F[A]
//  def writeRef[A](a: => A)(ref: => Ref[F[_], A]) : F[Unit]
//
////  -- | For monads supporting multi-threaded access (e.g., @IO@), this much be
////  -- an atomic modification.
////  atomicModifyRef' :: Ref m a -> (a -> (a, b)) -> m b
////  atomicModifyRef' sa f = do
////      a0 <- readRef' sa
////      let (a, b) = f a0
////      writeRef' sa a
////      return b
//
//  def atomicModifyRef[A, B](sa: Ref[F[_], A])(f: (A => (A, B))): F[B] = {
//    F.bind(readRef(sa))(a0 => {
//      val (a, b) = f(a0)
//      F.bind(writeRef(a)(sa))(_ => F.point(b))
//    })
//  }
//
//  def register(r: Resource[F], rel: F[Unit]): ResourceT[F, ReleaseKey] = ResourceT[F, Unit](
//    istate => r.resourceLiftBase(registerRef(istate, rel))
//  )
//
//  def registerRef[A](istate: Ref[F[_], ReleaseMap[F[_]]], rel: F[Unit]): F[ReleaseKey] = {
//    atomicModifyRef(istate)((rMap: ReleaseMap[F[_]]) => {
//      (ReleaseMap(rMap.key + 1, rMap.refCount, rMap.m.updated(rMap.key, rel)), ReleaseKey(rMap.key))
//    })
//  }
//}
//
//case class ResourceT[F[_], A](trans: HasRef.Ref[F[_], ReleaseMap[F[_]]] => F[A])
//
////case class ResourceT[F[_], A](trans: HasRef[F]#Ref[F[_], ReleaseMap[F[_]]] => F[A])
//case class ReleaseKey(key: Int)
//
//object ReleaseMap {
//  def apply[F[_], A](base: F[_]): ReleaseMap[F[_]] = ReleaseMap(0, 0, IntMap((0, base)))
//}
//case class ReleaseMap[A](key: Int, refCount: Int, m: Map[Int, A] = Map[Int, A]())
////
////trait ResourceT[F[_], A] {
////  implicit def F: Monad[F]
////
////  def value:
////}
//
//trait HasRefInstances {
//  implicit val ioHasRef = new HasRef[IO] {
//    implicit def F = IO.ioMonad
//
//    type Ref[F, A] = IORef[A]
//    def newRef[A](a: => A): IO[IORef[A]] = IO.newIORef(a)
//    def readRef[A](ref: => IORef[A]): IO[A] = ref.read
//    def writeRef[A](a: => A)(ref: => IORef[A]) = ref.write(a)
//  }
//
//  implicit def StHasRef[S] = new HasRef[({type λ[α] = ST[S, α]})#λ] {
//    type Ref[F, A] = STRef[S, A]
//    implicit def F: Monad[({type λ[α] = ST[S, α]})#λ] = ST.stMonad[S]
//
//    def newRef[A](a: => A) = ST.newVar(a)
//
//    def readRef[A](ref: => STRef[S, A]) = ref.read
//
//    def writeRef[A](a: => A)(ref: => STRef[S, A]) = ref.write(a).map(_ => ())
//  }
//}
//
//object hasRefs extends HasRefInstances
//
//trait ResourceInstances {
//  implicit val ioResource = new Resource[IO] {
//    implicit def F = ioMonad
//    implicit def hasRef = hasRefs.ioHasRef
//
//    def resourceLiftBase[A](base: IO[A]) = base
//
//    def resourceLiftBracket[A](init: IO[Unit], cleanup: IO[Unit], body: IO[A]): IO[A] =
//      ExceptionControl.bracket(init)(_ => cleanup)(_ => body)
//  }
//
////  Monad[({type λ[α] = ST[S, α]})#λ]
//  implicit def stResource[S] = new Resource[({type λ[α] = ST[S, α]})#λ] {
//    val stMonad: Monad[({type λ[α] = ST[S, α]})#λ] = ST.stMonad[S] /*type annotation to keep intellij more or less happy*/
//
//    implicit def F = stMonad
//    implicit def hasRef = hasRefs.StHasRef[S]
////    implicit def base[A]: Base[({type λ[α] = ST[S, α]})#λ, A] = new Base[({type λ[α] = ST[S, α]})#λ, A] {
////
////      implicit def F: Monad[({type λ[α] = ST[S, α]})#λ] = stMonad
////    }
//
//    def resourceLiftBase[A](base: ST[S, A]) = base
//
//    def resourceLiftBracket[A](ma: ST[S, Unit], mb: ST[S, Unit], mc: ST[S, A]) =
//      ma.flatMap(_ => mc.flatMap(c => mb.flatMap(_ => stMonad.point(c))))
//  }
//}
