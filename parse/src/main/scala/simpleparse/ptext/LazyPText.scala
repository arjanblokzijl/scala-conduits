package simpleparse
package ptext

import simpleparse.FoldUtils._
import scalaz.Functor


import PTextResult._
import text.{Text, LText}

sealed trait PTextResult[A] {
  def fold[Z](done: (=> LText, => A)=> Z, fail: (=> LText, => Stream[String], => String) => Z): Z

  def map[B](f: A => B): PTextResult[B] = fold(
    done = (t, a) => Done(t, f(a))
    , fail = (t, str, s) => Fail(t, str, s)
  )
}

object PTextResult {
  object Done {
    def apply[A](t: => LText, r: => A) = new PTextResult[A] {
      def fold[Z](done: (=> LText, => A)=> Z, fail: (=> LText, => Stream[String], => String) => Z): Z =
        done(t, r)
    }

    def unapply[A](pr: PTextResult[A]): Option[(LText, A)] = pr.fold((t, r) => Some(t, r), ToNone3)
  }

  object Fail {
    def apply[A](t: => LText, str: => Stream[String], msg: => String) = new PTextResult[A] {
      def fold[Z](done: (=> LText, => A)=> Z, fail: (=> LText, => Stream[String], => String) => Z): Z =
        fail(t, str, msg)
    }

    def unapply[A](pr: PTextResult[A]): Option[(LText, Stream[String], String)] = pr.fold(ToNone2, (t, str, msg) => Some(t, str, msg))
  }
}
trait PTextResultInstances {
  implicit def parseResultFunctor: Functor[({type l[r] = PTextResult[r]})#l] = new Functor[({type l[r] = PTextResult[r]})#l] {
    def map[A, B](r: PTextResult[A])(f: (A) => B) = r map f
  }

}
object LazyPText {

}

trait LazyPTextFunctions {
  import StrictPText._
  import LText._
  def parse[A](p: StrictPText.TParser[A])(s: => LText): PTextResult[A] = {
    def go(r: StrictPText.TResult[A], ys: LText): PTextResult[A] = r match {
      case ParseResult.Fail(x, stk, msg) => Fail(Chunk(x, ys), stk, msg)
      case ParseResult.Done(x, r) => Done(Chunk(x, ys), r)
      case ParseResult.Partial(k) => ys match {
        case Chunk(y, yss) => go(k(y), yss)
        case Empty() => go(k(Text.empty), LText.empty)
      }
    }
    s match {
      case LText.Chunk(x, xs) => go(StrictPText.parse(p, x), xs)
      case LText.Empty() => go(StrictPText.parse(p, Text.empty), LText.empty)
    }
  }

  def maybeResult[A](r: PTextResult[A]): Option[A] = r match {
    case Done(_, r) => Some(r)
    case _ => None
  }

  def eitherResult[A](r: PTextResult[A]): Either[String, A] = r match {
    case Done(_, r) => Right(r)
    case Fail(_, _, msg) => Left(msg)
  }

  def maybeP[A](p: StrictPText.TParser[A]): LText => Option[A] = t => maybeResult(parse(p)(t))

  def defP[A](p: StrictPText.TParser[A]): LText => PTextResult[A] = t => parse(p)(t)

  def char
}