package simpleparse

/**
 * User: arjan
 */
import ParseResult._
import scalaz.{Monoid, MonadPlus, Functor, Forall}
import scalaz.Free._
import scalaz.std.function._

import Parser._

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


  type ParseFailure[T, R] = (ParseState[T], Stream[String], String) => ParseResult[T, R]
  type ParseSuccess[T, B, R] = (ParseState[T], B) => ParseResult[T, R]
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
  type FA[R] = ParseFailure[T, R]
  type SA[R] = ParseSuccess[T, A, R]
  type PR[R] = ParseResult[T, R]

  def runParser(st: ParseState[T], kf: Forall[FA], ks: Forall[SA]): Trampoline[Forall[PR]]

  //simpler map, but writing it out seems a bit more efficient.
  //def map[B](f: A => B): Parser[T, B] = flatMap(a => Parser.returnP(f(a)))
  def map[B](f: A => B): Parser[T, B] = parser[T, B]((st, kf0, k) => {
    runParser(st, kf0, new Forall[SA] {
      def apply[C] = (s1: ParseState[T], a: A) =>
        k.apply(s1, f(a))
     })
  })

  def flatMap[B](f: A => Parser[T, B]): Parser[T, B] = parser[T, B]((s0, kf, ks) =>
    runParser(s0, kf, new Forall[SA] {
      def apply[C] = (s1: ParseState[T], a: A) =>
        f(a).runParser(s1, kf, ks).run.apply[C]
    })
  )

  /**
   * `plus` combines this parser with the supplied one.
   * This combinator implies choice: If this parser fails
   * without consuming any input, the second parser is tried.
   * This combinator is defined equal to `plus` of the [[scalaz.MonadPlus]] instance.
   */
  final def plus(b: Parser[T, A])(implicit M: Monoid[T]): Parser[T, A] = {
    parser((s0, kf, ks) => {
      noAdds(s0)(s1 => runParser(s1, new Forall[FA] {
         def apply[A] = (s1: ParseState[T], str: Stream[String], s: String) =>
           addS[T, PR[A]](s0)(s1)(s2 => b.runParser(s2, kf, ks).run.apply[A])
          }, ks))
    })
  }

  final def cons(n: => Parser[T, Stream[A]]): Parser[T, Stream[A]] = flatMap (x => n map (xs => x #:: xs))

  /**alias for `plus`.*/
  final def <|>(b: Parser[T, A])(implicit M: Monoid[T]): Parser[T, A] = plus(b)

  /**
   * `option` tries to apply this parser. If parsing fails without
   * consuming input, it returns the value `x`, otherwise the value
   * returned by this parser.
   */
  def option(x: A)(implicit M: Monoid[T]): Parser[T, A] =
    plus(Parser.returnP(x))

  /**
   * `many1` applies the action `p` one or more times. Returns
   * a list of the returned values of `p`.
   */
  def many1(implicit M: Monoid[T]): Parser[T, Stream[A]] =
    for {
      a <- this
      b <- many
    } yield a #:: b

  /**
   * `many` applies the action `p` zero or more times. Returns
   * a list of the returned values of `p`.
   */
  def many(implicit M: Monoid[T]): Parser[T, Stream[A]] = {
    def go(p: => Parser[T, A]): Parser[T, Stream[A]] = {
      lazy val many_p: Parser[T, Stream[A]] = (p cons many_p) <|> returnP[T, Stream[A]](Stream())
      many_p
    }
    go(this)
  }

  def sepBy1[S](s: Parser[T, S])(implicit M: Monoid[T]): Parser[T, Stream[A]] = {
    def scan: Parser[T, Stream[A]] =
      flatMap(a => s *> scan <|> returnP(Stream[A]()).map(s => a #:: s))

    scan
  }

  def sepBy[S](s: Parser[T, S])(implicit M: Monoid[T]): Parser[T, Stream[A]] =
    for {
      a <- this
      st <- (s *> sepBy1(s) <|> returnP(Stream[A]()))
    } yield a #:: st


  def endBy[S](s: Parser[T, S])(implicit M: Monoid[T]): Parser[T, Stream[A]] =
    (this <* s) many1

  /**
   * `manyTill` applies the parser zero or more times until
   * parser `end` succeeds, and returns the list of values returned by this parser.
   */
  def manyTill[B](end: Parser[T, B])(implicit M: Monoid[T]): Parser[T, List[A]] = {
    def scan: Parser[T, List[A]] = end *> returnP(List[A]()) <|> pm[T].map2(this, scan)(_ :: _)
    scan
  }

  /**
   * Skip zero or more instances of an action.
   */
  def skipMany(implicit M: Monoid[T]): Parser[T, Unit] = {
    def scan: Parser[T, Unit] = this *> scan <|> returnP(())
    scan
  }

  /**
   * Skip one or more instances of an action.
   */
  def skipMany1(implicit M: Monoid[T]): Parser[T, Unit] =
    this *> skipMany

  /**
   * Apply the parser repeatedly, returning every result.
   */
  def count(i: Int): Parser[T, Stream[A]] = {
    def go(n: Int, acc: Parser[T, Stream[A]]): Parser[T, Stream[A]] =
      if (n <= 0) acc
      else acc flatMap(_ => go(n - 1, acc))

    go(i, returnP(Stream[A]()))
  }

  /** Combine two alternatives.*/
  def eitherP[B](p : Parser[T, B])(implicit M: Monoid[T]): Parser[T, Either[A, B]] = {
    def left(a: A): Either[A, B] = Left(a)
    def right(b: B): Either[A, B] = Right(b)
    this.map(left(_)) <|> p.map(right(_))
  }

  def *>[B](p: Parser[T, B])(implicit M: Monoid[T]): Parser[T, B] =
    for {
      _ <- this
      a <- p
    } yield a


  def <*[B](p: Parser[T, B])(implicit M: Monoid[T]): Parser[T, A] =
    for {
      a <- this
      _ <- p
    } yield a

}

object Parser extends ParserFunctions with ParserInstances {
  import scalaz.Free._
  import scalaz.std.function._
  def pm[T](implicit M: Monoid[T]) = parserMonad[T]
  def apply[T, A](f: (ParseState[T], Forall[Parser[T, A]#FA], Forall[Parser[T, A]#SA]) => Forall[Parser[T, A]#PR]) = new Parser[T, A] {
    def runParser(st: ParseState[T], kf: Forall[Parser[T, A]#FA], ks: Forall[Parser[T, A]#SA]) = return_(f(st, kf, ks))
  }

  object parserSyntax extends scalaz.syntax.ToMonadPlusV
}

trait ParserInstances0 {

  implicit def parserMonoid[F, A](implicit M: Monoid[F]): Monoid[Parser[F, A]] = new Monoid[Parser[F, A]] {
    def append(f1: Parser[F, A], f2: => Parser[F, A]) = f1 plus f2

    def zero = failDesc("Monoid: zero")
  }
}

trait ParserInstances extends ParserInstances0 {
  implicit def parserMonad[F](implicit M: Monoid[F]): MonadPlus[({type l[r] = Parser[F, r]})#l] = new MonadPlus[({type l[r] = Parser[F, r]})#l] {
    def bind[A, B](fa: Parser[F, A])(f: (A) => Parser[F, B]) = fa flatMap f

    def point[A](a: => A) = Parser.returnP(a)

    def plus[A](a: Parser[F, A], b: => Parser[F, A]) = a plus b

    def empty[A] = failDesc("Monoid: zero")
  }

}

case class ParseState[T](input: T, added: T, more: More)

trait ParserFunctions {

  def parser[T, A](f: (ParseState[T], Forall[Parser[T, A]#FA], Forall[Parser[T, A]#SA]) => Trampoline[Forall[Parser[T, A]#PR]]) = new Parser[T, A] {
    def runParser(st: ParseState[T], kf: Forall[FA], ks: Forall[Parser[T, A]#SA]) = {
      f(st, kf, ks)
    }
  }
  def returnP[T, A](a: => A): Parser[T, A] = parser[T, A]((st, _kf, ks) => return_(new Forall[Parser[T, A]#PR] {
    def apply[A] = ks.apply[A](st, a)
  }))

  def addS[T, A](s0: ParseState[T])(s1: ParseState[T])(f: ParseState[T] => A)(implicit M : Monoid[T]): A = {
    val i = M.append(s0.input, s1.added)
    val a = M.append(s0.added, s1.added)
    val m = (s0.more, s1.more) match {
      case (Complete, _) => Complete
      case (_, Complete) => Complete
      case _ => Incomplete
    }
    f(ParseState(i, a, m))
  }

  def noAdds[T, A](s0: ParseState[T])(f: ParseState[T] => A)(implicit M : Monoid[T]): A =
    f(s0.copy(added = M.zero))


  def failDesc[T, A](err: String): Parser[T, A] = {
    parser[T, A]((s0, _kf, ks) => return_(new Forall[Parser[T, A]#PR] {
      def apply[A] = _kf.apply[A](s0, Stream.empty[String], "Failed reading: " + err)
    }))
  }

  /**
   * `choice` tries to apply the actions in the given list in order,
   * until one of them succeeds. Returns the value of the succeeding
   * action.
   */
  def choice[F, A](ps: Seq[Parser[F, A]])(implicit F: Monoid[F]): Parser[F, A] =
    ps.foldLeft(Monoid[Parser[F, A]].zero)((a, b) => a plus b)
}