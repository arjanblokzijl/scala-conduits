package simpleparse
package ptext

import text.Text

import simpleparse.{ParseResult => PR}
import simpleparse.{Parser => P}
import simpleparse.ParseResult.Partial
import scalaz.{DList, Forall}
import Parser._

object StrictPText extends StrictPTextFunctions

trait StrictPTextFunctions {
  type TParser[A] = Parser[Text, A]
  type TResult[A] = ParseResult[Text, A]
  type TInput = Input[Text]
  type TAdded = Added[Text]
  type TFailure[A] = PR.Failure[Text, A]
  type TSuccess[A, R] = PR.Success[Text, A, R]

  def failK[A]: TFailure[A] = (s0, stack, msg) => PR.Fail(s0.input, stack, msg)

  def successK[A]: TSuccess[A, A] = (s0, a) => PR.Done[Text, A](s0.input, a)

  def parse[A](p: TParser[A], s: Text): TResult[A] = p.runParser(ParseState(s, Text.empty, Incomplete),
       new Forall[TParser[A]#FA] {
         def apply[A] = failK[A]
       },
       new Forall[TParser[A]#SA] {
         //TODO can this be ever unsafe?
         def apply[A] = (s0, a) => PR.Done[Text, A](s0.input, a.asInstanceOf[A])
       }).run.apply[A]

  /**Alias for `label`.*/
  def ?[A](p: TParser[A], msg0: String): TParser[A] = label(p, msg0)

  /**Name the parser in case a failure occurs.*/
  def label[A](p: TParser[A], msg: String): TParser[A] = {
    Parser[Text, A]((s0, kf, ks) => new Forall[Parser[Text, A]#PR] {
      def apply[A] = p.runParser(s0, new Forall[Parser[Text, A]#FA] {
        def apply[A] = (s1, strs, msg1) => kf.apply(s1, msg #:: strs, msg1)
      }, ks).run.apply[A]
    })
  }

  /**Match a specific character.*/
  def char(c: Char): TParser[Char] = label(satisfy(_ == c), c.toString)

  /**Match any character.*/
  def anyChar: TParser[Char] = satisfy(_ => true)

  /**Match any character, except the given one.*/
  def notChar(c: Char): TParser[Char] = label(satisfy(_ != c), "not " + c)

  /**Match any character, except the given ones.*/
  def noneOf(cs: Seq[Char]): TParser[Char] =
    label(satisfy(c => !cs.contains(c)), "noneOf " + cs)

  /**
   * Math either a single newline character `\n` or a
   * carriage return followed by a newline character `\r\n`.
   */
   def endOfLine: TParser[Unit] =
     char('\n').map(_ => ()) <|> (string(Text.fromChars("\r\n")).map(_ => ()))

  /**
   * The parser `satisfy p` succeeds for any character for which the
   * predicate `p` returns 'True'. Returns the character that is
   * actually parsed.
   */
  def satisfy(p: Char => Boolean): TParser[Char] =
     ensure(1).flatMap(s => {
       val w = s.head
       if (p(w)) put(s.tail).flatMap(_ => Parser.returnP(w))
       else fail("satisfy")
     })

  /**
   * The parser `satisfy p` succeeds for any character for which the
   * predicate `p` returns 'True'. Returns the character that is
   * actually parsed.
   */
  def satisfyWith[A](f: Char => A)(p: A => Boolean): TParser[A] =
     ensure(1).flatMap(s => {
       val w = f(s.head)
       if (p(w)) put(s.tail).flatMap(_ => Parser.returnP(w))
       else fail("satisfyWith")
     })

  import scalaz.Free._
  import scalaz.std.function._

  /**A parser that always runs the failure continuation.*/
  def fail[A](msg: String): TParser[A] =
    parser[Text, A]((s0, kf, ks) => return_(new Forall[Parser[Text, A]#PR] {
      def apply[A] = kf.apply(s0, Stream(msg), msg)
    }))

  def put(s: Text): TParser[Unit] =
    Parser[Text, Unit]((s0, kf, ks) => new Forall[Parser[Text, Unit]#PR] {
      def apply[A] = ks.apply(s0.copy(input = s), ())
    })

  def get: TParser[Text] =
    Parser[Text, Text]((s0, kf, ks) => new Forall[Parser[Text, Text]#PR] {
      def apply[A] = ks.apply(s0, s0.input)
    })

  def takeWith(n: Int, p: Text => Boolean): TParser[Text] = {
      ensure(n).flatMap(s => {
        val h = s.take(n)
        val t = s.drop(n)
        if (p(h)) put(t).flatMap(_ => Parser.returnP(h))
        else fail("takeWith")
      })
  }

  def take(n: Int): TParser[Text] = takeWith(n, _ => true)

  def takeRest: TParser[List[Text]] = {
    def go(acc: DList[Text]): TParser[List[Text]] = wantInput.flatMap(input =>
      if (input) get.flatMap(s => {
        put(Text.empty).flatMap(_ => go(acc :+ s))
      }) else Parser.returnP[Text, List[Text]](acc.toList)
    )
    go(DList())
  }

  /**
   * The parser skip succeeds for any character
   * for which the predicate `p` returns true.
   */
  def skip(p: Char => Boolean): TParser[Unit] =
    ensure(1).flatMap(s =>
      if (p(s.head)) put(s.tail)
      else fail("skip")
    )

  /**Skip past the input a long as the given predicate is true.*/
  def skipWhile(p: Char => Boolean): TParser[Unit] = {
    def go: TParser[Unit] = get.flatMap(text => {
      val t = text.dropWhile(p)
      put(t).flatMap(_ => if (t.isEmpty) wantInput.flatMap(input =>
                             if (input) go else Parser.parserMonad[Text].point(()))
                          else Parser.parserMonad[Text].point(()))
    })
    go
  }

  /**
   * Consume input as long as the given predicate returns true.
   * This parser does not fail: It returns an empty string if the predicate
   * returns false on the first character of the input.
   */
  def takeWhile(p: Char => Boolean): TParser[Text] = {
    def go(acc: List[Text]): TParser[List[Text]] =
      get.flatMap(text => {
        val (h, t) = text.span(p)
        put(t).flatMap(_ => if (t.isEmpty && !h.isEmpty) wantInput.flatMap(input => {
                              if (input) go(h :: acc) else Parser.returnP(h :: acc)})
                            else Parser.returnP(h :: acc))
      })
    go(List()).map((tss: List[Text]) => Text.concat(tss.reverse))
  }

  /**
   * Consume input as long as the given predicate returns false.
   * This parser does not fail: It returns an empty string if the predicate
   * returns true on the first character of the input.
   */
  def takeTill(p: Char => Boolean): TParser[Text] =
    takeWhile(!p(_))

  def string(s: Text): TParser[Text] = takeWith(s.length, Text.textInstance.equal(s, _))

  import Parser._
  import scalaz.Free._
  import scalaz.std.function._
  /**If at least n characters are available, return the input, else fail.*/
  def ensure(n: Int): TParser[Text] =
    parser[Text, Text]((s0, kf, ks) => return_(new Forall[Parser[Text, Unit]#PR] {
      def apply[A] = if (s0.input.length >= n) ks.apply(s0, s0.input)
                     else demandInput.flatMap(_ => ensure(n)).runParser(s0, kf, ks).run.apply[A]
    }))


  /**Ask for input. If we receive any, pass it to a success continuation, otherwise to a failure continuation.*/
  def prompt[A](s0: ParseState[Text])(kf: ParseState[Text] => TResult[A])(ks: ParseState[Text] => TResult[A]): TResult[A] =
    Partial[Text, A](s => if (s.isEmpty) kf(s0.copy(more = Complete))
                          else ks(s0.copy(input = s0.input.append(s), added = s0.added.append(s), more = Incomplete)))

  /**Demand more input via a `Partial` continuation.*/
  def demandInput: TParser[Unit] =
    Parser[Text, Unit]((s0, kf, ks) => new Forall[Parser[Text, Unit]#PR] {
        def apply[A] = s0.more match {
           case Complete => kf.apply(s0, Stream("demandInput"), "not enough input")
           case Incomplete => {
              prompt(s0)(s1 => kf.apply[A](s1, Stream("demandInput"), "not enough input"))(s2 => ks.apply(s2, ()))
           }
         }
      })

  /**
   * This parser always succeeds.  It returns 'True' if any input is
   * available either immediately or on demand, and 'False' if the end
   * of all input has been reached.
   */
  def wantInput: TParser[Boolean] =
    Parser[Text, Boolean]((s0, kf, ks) => new Forall[TParser[Boolean]#PR] {
        def apply[A] =
          if (!s0.input.isEmpty) ks.apply(s0, true)
          else s0.more match {
           case Complete => ks.apply(s0, false)
           case Incomplete => {
              prompt(s0)(s1 => ks.apply[A](s1, false))(s2 => ks.apply(s2, true))
           }
         }
      })

  def endOfInput: TParser[Unit] = {
    import Parser.addS
    parser[Text, Unit]((s0, kf, ks) => return_(new Forall[TParser[Unit]#PR] {
      def apply[A] =
        if (!s0.input.isEmpty) kf.apply(s0, Stream(), "endOfInput")
        else s0.more match {
          case Complete => ks.apply(s0, ())
          case Incomplete => demandInput.runParser(s0,
            new Forall[TParser[Unit]#FA] {
              def apply[A] = (s1, str, s) => addS(s0)(s1)(s2 => ks.apply(s2, ()))
            },
            new Forall[TParser[Unit]#SA] {
              def apply[A] = (s1, b) => addS(s0)(s1)(s2 => kf.apply(s2, Stream(), "endOfInput"))
            }).run.apply[A]
        }
    }))
  }
}