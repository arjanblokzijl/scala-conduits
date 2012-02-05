package conduits

import scalaz.{Functor, Monad}


//data PreparedSink input m output =
//    SinkNoData output
//  | SinkData
//        { sinkPush :: input -> ResourceT m (SinkResult input output)
//        , sinkClose :: ResourceT m output
//        }
sealed trait PreparedSink[I, F[_], O]
case class SinkNoData[I, F[_], O](output: O) extends PreparedSink[I, F, O]
//trait SinkData[I, F[_], O] extends PreparedSink[I, F, O] {
//  def sinkPush(input: I): ResourceT[F, SinkResult[I, O]]
//  def sinkClose: ResourceT[F, SinkResult[I, O]]
//}
case class SinkData[I, F[_], O](sinkPush: I => ResourceT[F, SinkResult[I, O]],
                                sinkClose: ResourceT[F, SinkResult[I, O]]) extends PreparedSink[I, F, O]


trait SinkResult[I, O]
case class Processing[I, O]() extends SinkResult[I, O]
case class Done[I, O](input: Option[I], output: O) extends SinkResult[I, O]

case class Sink[I, F[_], O](prepare: ResourceT[F, PreparedSink[I, F, O]])

trait SinkInstances {
  implicit def preparedSinkFunctor[I, F[_]]: Functor[({type l[a] = PreparedSink[I, F, a]})#l] = new Functor[({type l[a] = PreparedSink[I, F, a]})#l] {

    def map[A, B](fa: PreparedSink[I, F, A])(f: (A) => B): PreparedSink[I, F, B] = fa match {
      case SinkNoData(o) => SinkNoData(f(o))
      case SinkData(p, c) => SinkData(sinkPush = sys.error(""), sinkClose = sys.error(""))
    }
  }
}