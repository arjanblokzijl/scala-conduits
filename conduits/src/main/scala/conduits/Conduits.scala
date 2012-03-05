package conduits

/**
* User: arjan
*/

import resourcet.resource
import resource._
import sinks.{SinkPush, SinkClose}
import scalaz.Monad

object Conduits {
  def normalConnect[F[_], A, B](source: Source[F, A], sink: Sink[A, F, B])(implicit M: Monad[F]): F[B] = (source, sink) match {
    case (src, Done(leftover, output)) => M.map(src.sourceClose)(_ => output)
    case (src, SinkM(msink)) => M.bind(msink)(s => normalConnect(src, s))
    case (SourceM(msrc, _), sink) => M.bind(msrc)(src => normalConnect(src, sink))
    case (Closed(), Processing(_, close)) => close
    case (Open(src, _, a), Processing(push, _)) => normalConnect(src, push(a))
  }

}