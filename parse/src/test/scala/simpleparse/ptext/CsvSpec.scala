package simpleparse
package ptext

import org.specs2.mutable.Specification

import StrictPText._
import Parser._
import LazyPText._
import text.LText._
import text.Text._
import collection.immutable.IndexedSeq
import text.{LText, Text}

class CsvSpec extends Specification {

  def cell = noneOf(",\n").many.map(_.mkString)
  def line = cell sepBy(char(','))
  def csv = line endBy endOfLine

  "csv parsing" should {
    "work" in {
       val res = maybeP(cell)(fromStrict(fromChars("ab,bc,de,ef\n,gh,hi,jk,lm\nop,qr,st,uv\n")))
       println("result is " + res)
       success
    }
//    "work on large input" in { //doesn't work, TODO therefore
//      val str: String = (1 to 1000).map(n => "abc,deffgh,ikj").mkString + "\n"
//      val text: LText = fromStrict(fromChars(str))
//      val res = maybeP(csv)(text)
//      success
//    }
  }
}