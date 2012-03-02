package conduits

/**
* User: arjan
*/
import resource._
import sinks.{SinkPush, SinkClose}
import scalaz.Monad

object Conduits {
  def normalConnect[F[_], A, B](source: Source[F, A], sink: Sink[A, F, B])(implicit M: Monad[F]): F[B] = {
    def connect1(source: Source[F, A], push: SinkPush[A, F, B], close: SinkClose[A, F, B]): F[B]  = {
      M.bind(source.sourcePull)((res: SourceResult[F, A]) => res match {
        case Closed() => M.bind(close)((res1: B) => M.point(res1))
        case Open(src1, a) => M.bind(push(a))((mres: SinkResult[A, F, B]) => mres match {
          case Done(leftover, res1) => M.bind(src1.sourceClose)(_ => M.point(res1))
          case Processing(push1, close1) => connect1(src1, push1, close1)
        })
      })
    }
    (source, sink) match {
      case (_, SinkNoData(output)) => M.point(output)
      case (src0, SinkLift(msink)) => M.bind(msink)(s => normalConnect(src0, s))
      case (src0, SinkData(push0, close0)) => connect1(src0, push0, close0)
    }
  }
}