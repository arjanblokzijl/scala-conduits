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
       val res = maybeP(csv)(fromStrict(fromChars("ab,bc,de,ef\ngh,hi,jk,lm\nop,qr,st,uv\n")))
       res must be_==(Some(List(List("ab", "bc", "de", "ef"), List("gh", "hi", "jk", "lm"), List("op", "qr", "st", "uv"))))
    }
//    "work on large input" in { //gives SOE, TODO therefore
//      val str: String = (1 to 1000).map(n => "abc,deffgh,ikj").mkString + "\n"
//      val text: LText = fromStrict(fromChars(str))
//      val res = maybeP(line)(text)
//      println("result is " + res)
//      success
//    }
  }
}