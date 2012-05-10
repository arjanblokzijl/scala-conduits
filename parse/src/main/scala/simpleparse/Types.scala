package simpleparse

/**
 * User: arjan
 */
import ParseResult._
import scalaz.{Monoid, Monad, Functor, Forall}

sealed trait ParseResult[T, A] {
  def fold[Z](done: (=> T, => A)=> Z, partial: (=> T => ParseResult[T, A]) => Z, fail: (=> T, => Stream[String], => String) => Z): Z

  def map[B](f: A => B): ParseResult[T, B] = fold(
    done = (t, a) => Done(t, f(a))
    , partial = k => Partial[T, B](p => k.apply(p).map(f))
    , fail = (t, str, s) => Fail(t, str, s)
  )
}

import FoldUtils._
object ParseResult {

  object Done {
    def apply[T, A](t: => T, r: => A) = new ParseResult[T, A] {
      def fold[Z](done: (=> T, => A) => Z, partial: (=> T => ParseResult[T, A]) => Z, fail: (=> T, => Stream[String], => String) => Z): Z =
        done(t, r)
    }

    def unapply[T, A](pr: ParseResult[T, A]): Option[(T, A)] = pr.fold((t, r) => Some(t, r), ToNone1, ToNone3)
  }

  object Partial {
    def apply[T, A](p: => T => ParseResult[T, A]) = new ParseResult[T, A] {
      def fold[Z](done: (=> T, => A) => Z, partial: (=> T => ParseResult[T, A]) => Z, fail: (=> T, => Stream[String], => String) => Z): Z =
        partial(p)
    }

    def unapply[T, A](pr: ParseResult[T, A]): Option[(T => ParseResult[T, A])] = pr.fold(ToNone2, r => Some(r), ToNone3)
  }

  object Fail {
    def apply[T, A](t: => T, str: => Stream[String], s: => String) = new ParseResult[T, A] {
      def fold[Z](done: (=> T, => A) => Z, partial: (=> T => ParseResult[T, A]) => Z, fail: (=> T, => Stream[String], => String) => Z): Z =
        fail(t, str, s)
    }

    def unapply[T, A](pr: ParseResult[T, A]): Option[(T, Stream[String], String)] = pr.fold(ToNone2, ToNone1, (t, st, s) => Some(t, st, s))
  }

//  type Failure[T, R] = Input[T] => Added[T] => More => Stream[String] => String => ParseResult[T, R]
  type Failure[T, R] = (Input[T], Added[T], More, Stream[String], String) => ParseResult[T, R]
  type Success[T, B, R] = (Input[T], Added[T], More, B) => ParseResult[T, R]
}

trait ParseResultInstances {
  implicit def parseResultFunctor[F]: Functor[({type l[r] = ParseResult[F, r]})#l] = new Functor[({type l[r] = ParseResult[F, r]})#l] {
    def map[A, B](r: ParseResult[F, A])(f: (A) => B) = r map f
  }
}

case class Input[T](unI: T)
case class Added[T](unA: T)

sealed trait More
case object Complete extends More
case object Incomplete extends More

import Parser._
trait Parser[T, A] {
  type FA[R] = Failure[T, R]
  type SA[R] = Success[T, A, R]
  type PR[R] = ParseResult[T, R]

//  def runParser(i: Input[T], a: Added[T], m: More, kf: Forall[FA], ks: Forall[SA]): Forall[PR]
  def runParser(i: Input[T], a: Added[T], m: More, kf: Forall[FA], ks: Forall[({type l[r] = Success[T, A, r]})#l]): Forall[PR]

  def flatMap[B](f: A => Parser[T, B]): Parser[T, B] = Parser[T, B]((i0, a0, m0, kf, ks) =>
    runParser(i0, a0, m0, kf, new Forall[SA] {
      def apply[C] = (i1: Input[T], a1: Added[T], m1: More, a: A) =>
        f(a).runParser(i1, a1, m1, kf, ks).apply[C]
    })
  )

  def plus(b: Parser[T, A])(implicit M: Monoid[T]): Parser[T, A] = {
    Parser((i0, a0, m0, kf, ks) => {
      noAdds(i0, a0, m0)((i1, a1, m1) => runParser(i1, a1, m1, new Forall[FA] {
         def apply[A] = (i1: Input[T], a1: Added[T], m1: More, str: Stream[String], s: String) =>
           addS[T, PR[A]](i0, a0, m0)(i1, a1, m1)((i2, a2, m2) => b.runParser(i2, a2, m2, kf, ks).apply[A])
          }, ks))
    })
  }
}

object Parser extends ParserFunctions with ParserInstances {
  def apply[T, A](f: (Input[T], Added[T], More, Forall[Parser[T, A]#FA], Forall[Parser[T, A]#SA]) => Forall[Parser[T, A]#PR]) = new Parser[T, A] {
    def runParser(i: Input[T], a: Added[T], m: More, kf: Forall[({type l[r] = Failure[T, r]})#l], ks: Forall[({type l[r] = Success[T, A, r]})#l]) = f(i, a, m, kf, ks)
  }
}

trait ParserInstances {
  implicit def parserMonad[F]: Monad[({type l[r] = Parser[F, r]})#l] = new Monad[({type l[r] = Parser[F, r]})#l] {
    def bind[A, B](fa: Parser[F, A])(f: (A) => Parser[F, B]) = fa flatMap f

    def point[A](a: => A) = Parser.returnP(a)
  }

  implicit def parserMonoid[F, A](implicit M: Monoid[F]): Monoid[Parser[F, A]] = new Monoid[Parser[F, A]] {
    def append(f1: Parser[F, A], f2: => Parser[F, A]) = f1 plus f2

    def zero = failDesc("Monoid: zero")
  }
}

trait ParserFunctions {
  def returnP[T, A](a: => A): Parser[T, A] = Parser[T, A]((i0, a0, m0, _kf, ks) => new Forall[Parser[T, A]#PR] {
    def apply[A] = ks.apply(i0, a0, m0, a)
  })

  def addS[T, A](i0: => Input[T], a0: => Added[T], m0: => More)(_i1: => Input[T], a1: => Added[T], m1: => More)(f: (=> Input[T], => Added[T], => More) => A)(implicit M : Monoid[T]): A = {
    val i = Input(M.append(i0.unI, a1.unA))
    val a = Added(M.append(a0.unA, a1.unA))
    val m = (m0, m1) match {
      case (Complete, _) => Complete
      case (_, Complete) => Complete
      case _ => Incomplete
    }
    f(i, a, m)
  }

  def noAdds[T, A](i0: => Input[T], a0: => Added[T], m0: => More)(f: (=> Input[T], => Added[T], => More) => A)(implicit M : Monoid[T]): A =
    f(i0, Added(M.zero), m0)

  def failDesc[T, A](err: String): Parser[T, A] = {
    Parser[T, A]((i0, a0, m0, _kf, ks) => new Forall[Parser[T, A]#PR] {
        def apply[A] = _kf.apply(i0, a0, m0, Stream.empty[String], "Failed reading: " + err)
    })
  }
}