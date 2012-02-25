package conduits

/**
* User: arjan
*/
import resource._
import sinks.{SinkPush, SinkClose}
import scalaz.Monad

object Conduits {
  def normalConnect[F[_], A, B](source: Source[F, A], sink: Sink[A, F, B])(implicit M: Monad[F]): ResourceT[F, B] = {
//    val rtm = resourceTMonad[F]
//    def connect1(source: Source[F, A], push: SinkPush[A, F, B], close: SinkClose[A, F, B]): ResourceT[F, B]  = {
//      rtm.bind(source.sourcePull)((res: SourceResult[F, A]) => res match {
//        case Closed() => rtm.bind(close)((res1: B) => rtm.point(res1))
//        case Open(src1, a) => rtm.bind(push(a))((mres: SinkResult[A, F, B]) => mres match {
//          case Done(leftover, res1) => rtm.bind(src1.sourceClose)(_ => rtm.point(res1))
//          case Processing(push1, close1) => connect1(src1, push1, close1)
//        })
//      })
//    }
//    (source, sink) match {
//      case (_, SinkNoData(output)) => rtm.point(output)
//      case (src0, SinkLift(msink)) => rtm.bind(msink)(s => normalConnect(src0, s))
//      case (src0, SinkData(push0, close0)) => connect1(src0, push0, close0)
//    }
    sys.error("")
  }
}