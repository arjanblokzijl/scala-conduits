package simpleparse
package ptext

import text.Text

import simpleparse.{ParseResult => PR}
import simpleparse.{Parser => P}
import scalaz.Forall
import simpleparse.ParseResult.Partial

object StrictPText extends StrictPTextFunctions {

}

trait StrictPTextFunctions {
  type TParser[A] = Parser[Text, A]
  type TResult[A] = ParseResult[Text, A]
  type TInput = Input[Text]
  type TAdded = Added[Text]
  type TFailure[A] = PR.Failure[Text, A]
  type TSuccess[A, R] = PR.Success[Text, A, R]

  def failK[A]: TFailure[A] = (i0, _a0, _m0, stack, msg) => PR.Fail(i0.unI, stack, msg)

  def successK[A]: TSuccess[A, A] = (i0, _a0, _m0, a) => PR.Done[Text, A](i0.unI, a)

  def parse[A](p: TParser[A], s: Text): TResult[A] = p.runParser(Input(s), Added(Text.empty), Incomplete,
       new Forall[TParser[A]#FA] {
         def apply[A] = failK[A]
       },
       new Forall[TParser[A]#SA] {
         //TODO can this be ever unsafe?
         def apply[A] = (i0, _a0, _m0, a) => PR.Done[Text, A](i0.unI, a.asInstanceOf[A])
       }).apply[A]

  def ?[A](p: TParser[A], msg0: String): TParser[A] = label(p, msg0)

  def label[A](p: TParser[A], msg0: String): TParser[A] = {
    Parser[Text, A]((i0, a0, m0, kf, ks) => new Forall[Parser[Text, A]#PR] {
      def apply[A] = p.runParser(i0, a0, m0, new Forall[Parser[Text, A]#FA] {
        def apply[A] = (i, a, m, strs, msg) => kf.apply(i, a, m, msg0 #:: strs, msg)
      }, ks).apply[A]
    })
  }

  def char(c: Char): TParser[Char] = label(satisfy(_ == c), c.toString)

  def anyChar: TParser[Char] = satisfy(_ => true)


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

  /**A parser that always runs the failure continuation.*/
  def fail[A](msg: String): TParser[A] =
    Parser[Text, A]((i0, a0, m0, kf, ks) => new Forall[Parser[Text, A]#PR] {
      def apply[A] = kf.apply(i0, a0, m0, Stream(msg), msg)
    })

  def put(s: Text): TParser[Unit] =
    Parser[Text, Unit]((i0, a0, m0, kf, ks) => new Forall[Parser[Text, Unit]#PR] {
      def apply[A] = ks.apply(Input(s), a0, m0, ())
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

  def ensure(n: Int): TParser[Text] =
    Parser[Text, Text]((i0, a0, m0, kf, ks) => new Forall[Parser[Text, Unit]#PR] {
      def apply[A] = if (i0.unI.length >= n) ks.apply(i0, a0, m0, i0.unI)
                     else demandInput.flatMap(_ => ensure(n)).runParser(i0, a0, m0, kf, ks).apply[A]
    })


  /**Ask for input. If we receive any, pass it to a success continuation, otherwise to a failure continuation.*/
  def prompt[A](i0: TInput, a0: TAdded, m0: More)(kf: (TInput, TAdded, More) => TResult[A])(ks: (TInput, TAdded, More) => TResult[A]): TResult[A] =
    Partial[Text, A](s => if (s.isEmpty) kf(i0, a0, Complete)
                          else ks(Input(i0.unI.append(s)), Added(a0.unA.append(s)), Incomplete))

  def demandInput: TParser[Unit] =
    Parser[Text, Unit]((i0, a0, m0, kf, ks) => new Forall[Parser[Text, Unit]#PR] {
        def apply[A] = m0 match {
           case Complete => kf.apply(i0, a0, m0, Stream("demandInput"), "not enough input")
           case Incomplete => {
              prompt(i0, a0, m0)((i, a, m) => kf.apply[A](i, a, m, Stream("demandInput"), "not enough input"))((i, a, m) => ks.apply(i, a, m, ()))
           }
         }
      })
}