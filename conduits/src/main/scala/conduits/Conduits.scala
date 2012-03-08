package conduits

/**
* User: arjan
*/
import scalaz.Monad

trait ConduitsFunctions {
  //TODO move this to Source?
  def normalConnect[F[_], A, B](source: Source[F, A], sink: Sink[A, F, B])(implicit M: Monad[F]): F[B] = (source, sink) match {
    case (src, Done(leftover, output)) => M.map(src.sourceClose)(_ => output)
    case (src, SinkM(msink)) => M.bind(msink)(s => normalConnect(src, s))
    case (SourceM(msrc, _), sink) => M.bind(msrc)(src => normalConnect(src, sink))
    case (Closed(), Processing(_, close)) => close
    case (Open(src, _, a), Processing(push, _)) => normalConnect(src, push(a))
  }

  def normalFuseLeft[F[_], A, B](source: Source[F, A], conduit: Conduit[A, F, B])(implicit M: Monad[F]): Source[F, B] = (source, conduit) match {
    case (Closed(), Running(_, close)) => close
    case (Closed(), Finished(_)) => Closed[F, B]()
    case (src, Finished(_)) => SourceM(M.map(src sourceClose)(_ => Closed()), src sourceClose)
    case (src, HaveMore(p, close, x)) => Open[F, B](normalFuseLeft(src, p), M.bind(src.sourceClose)(_ => close), x)
    case (SourceM(msrc, closeS), c@Running(_, closeC)) => SourceM(M.map(msrc)(s => normalFuseLeft(s, c)), M.bind(closeS)(_ => (closeC.sourceClose)))
    case (Open(src, _, a), Running(push, _)) => normalFuseLeft(src, push(a))
    case (src, ConduitM(mcon, conclose)) => SourceM[F, B]( M.map(mcon)(c => normalFuseLeft(src, c)), M.bind(conclose)(_ => (src.sourceClose)))
  }
}

trait ConduitsInstances {
  trait IsSource[F[_], A, S] {
    def connect[B](src: S, sink: Sink[A, F, B])(implicit M: Monad[F]): F[B]
  }

  implicit def sourceIsSource[F[_], A, B](implicit M: Monad[F]): IsSource[F, A, Source[F, A]] = new IsSource[F, A, Source[F, A]] {
    def connect[B](src: Source[F, A], sink: Sink[A, F, B])(implicit M: Monad[F]) = Conduits.normalConnect(src, sink)
  }
}

object Conduits extends ConduitsInstances with ConduitsFunctions

