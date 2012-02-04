package conduits

/**
 * User: arjan
 */
//data PreparedSink input m output =
//    SinkNoData output
//  | SinkData
//        { sinkPush :: input -> ResourceT m (SinkResult input output)
//        , sinkClose :: ResourceT m output
//        }
//sealed trait PreparedSink[I, F[_], G[_], O]
//case class SinkNoData[I, F[_], G[_], O]() extends PreparedSink[I, F, G, O]
//trait SinkData[I, F[_], G[_], O] extends PreparedSink[I, F, G, O] {
//  def sinkPush(input: I): ResourceT[F, G, SinkResult[I, O]]
//  def sinkClose: ResourceT[F, G, SinkResult[I, O]]
//}
//
//trait SinkResult[I, O]
//case class Processing[I, O]() extends SinkResult[I, O]
//case class Done[I, O](input: Option[I], output: O) extends SinkResult[I, O]
//
//case class Sink[I, F[_], G[_], O](prepare: ResourceT[F, G, PreparedSink[I, F, G, O]])
