package conduits

import scalaz.{Functor, Monad}

import sinks._
import resource._

sealed trait PreparedSink[I, F[_], O]
case class SinkNoData[I, F[_], O](output: O) extends PreparedSink[I, F, O]
case class SinkData[I, F[_], O](sinkPush: SinkPush[I, F, O],
                                sinkClose: SinkClose[I, F, O]) extends PreparedSink[I, F, O]

trait SinkResult[I, O] {
  def map[B](f: O => B) = this match {
    case Processing() => Processing[I, B]()
    case Done(input, output) => Done(input, f(output))
  }
}
case class Processing[I, O]() extends SinkResult[I, O]
case class Done[I, O](input: Option[I], output: O) extends SinkResult[I, O]

case class Sink[I, F[_], O](prepare: ResourceT[F, PreparedSink[I, F, O]]) {
//  def map[B](f: O => B) = {
//    resourceTMonad[F].map[PreparedSink[I, F, O], PreparedSink[I, F, B]](prepare)((r: PreparedSink[I, F, O]) => sys.error(""))
//  }
}

trait SinkInstances {

  implicit def sinkResultFunctor[I]: Functor[({type l[a] = SinkResult[I, a]})#l] = new Functor[({type l[a] = SinkResult[I, a]})#l] {
    def map[A, B](fa: SinkResult[I, A])(f: (A) => B): SinkResult[I, B] = fa match {
      case Processing() => Processing[I, B]()
      case Done(input, output) => Done(input, f(output))
    }
  }

  implicit def preparedSinkFunctor[I, F[_]](implicit M: Monad[F]): Functor[({type l[a] = PreparedSink[I, F, a]})#l] = new Functor[({type l[a] = PreparedSink[I, F, a]})#l] {
    def map[A, B](fa: PreparedSink[I, F, A])(f: (A) => B): PreparedSink[I, F, B] = fa match {
      case SinkNoData(o) => SinkNoData(f(o))
      case SinkData(p, c) => SinkData(sinkPush = i =>
                                                  resourceTMonad[F].map[SinkResult[I, A], SinkResult[I, B]](p(i))((r: SinkResult[I, A]) => r.map(f)),
                                      sinkClose = resourceTMonad[F].map[SinkResult[I, A], SinkResult[I, B]](c)((r: SinkResult[I, A]) => r.map(f))
                                      )
    }
  }

  implicit def sinkFunctor[I, F[_], O](implicit M: Monad[F]): Functor[({type l[a] = Sink[I, F, a]})#l] = new Functor[({type l[a] = Sink[I, F, a]})#l] {
    def map[A, B](fa: Sink[I, F, A])(f: (A) => B): Sink[I, F, B] =
      Sink(resourceTMonad[F].map[PreparedSink[I, F, A], PreparedSink[I, F, B]](fa.prepare)(r =>
             preparedSinkFunctor[I, F].map(r)(a => f(a))))
  }
}

trait SinkFunctions {
  type SinkPush[I, F[_], O] = I => ResourceT[F, SinkResult[I, O]]
  type SinkClose[I, F[_], O] = ResourceT[F, SinkResult[I, O]]
}

object sinks extends SinkFunctions with SinkInstances