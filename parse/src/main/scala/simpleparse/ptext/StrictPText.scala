package simpleparse
package ptext

import text.Text

import simpleparse.{ParseResult => PR}
import simpleparse.{Parser => P}
import scalaz.Forall

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
}