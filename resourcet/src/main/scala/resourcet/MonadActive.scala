package resourcet

import scalaz._
import effect.{ST, MonadIO, IO}
import scalaz.Kleisli._


/**
 * Determine if some monad is still active. This is intended to prevent usage
 * of a monadic state after it has been closed, which is necessary for such
 * cases as lazy IO, where an unevaluated thunk may still refer to a closed ResourceT.
 */
trait MonadActive[F[_]] {
  implicit def F: Monad[F]

  def monadActive: F[Boolean]
}

object MonadActive {
  implicit def idMonadActive: MonadActive[Id] = new MonadActive[scalaz.Id] {
    implicit def F = Id.id

    def monadActive = true
  }
  implicit def ioMonadActive: MonadActive[IO] = new MonadActive[IO] {
    implicit def F = IO.ioMonad

    def monadActive = IO.ioMonad.point(true)
  }

  implicit def rtMonadActive[F[_]](implicit M: Monad[F], MO: MonadIO[F], MA: MonadActive[F]): MonadActive[({type l[a] = ResourceT[F, a]})#l] = new MonadActive[({type l[a] = ResourceT[F, a]})#l]  {
    implicit def F = resource.resourceTMonad[F]

    def monadActive: ResourceT[F, Boolean] = ResourceT[F, Boolean](kleisli(rmMap =>
      M.bind(MO.liftIO(rmMap.read))(rm => rm match {
        case ReleaseMapClosed => M.point(false)
        case _ => MA.monadActive
      })
    ))
  }

  implicit def stMonadActive[S]: MonadActive[({type l[a] = ST[S, a]})#l] = new MonadActive[({type l[a] = ST[S, a]})#l]  {
    implicit def F = ST.stMonad[S]

    def monadActive = ST.stMonad[S].point(true)
  }
}
