package simpleparse
package ptext

import org.specs2.mutable.Specification
import text.{Text, LText}
import simpleparse.ptext.StrictPText._
import simpleparse.ptext.LazyPText._
import LText._
import Text._
import org.specs2.ScalaCheck
import ptext.PTextResult.{Fail, Done}

/**
 * User: arjan
 */

class ParseTextSpec extends Specification with ScalaCheck {
  "parse char" ! check {(chars: Array[Char]) =>
    val p = if (chars.isEmpty) fail("no chars") else char(chars.head)
    val actual = maybeP(p)(fromStrict(fromChars(chars)))
    val expected = if (chars.isEmpty) None else Some(chars.head)
    actual must be_==(expected)
  }

  "take" ! check {(i: Int, chars: Array[Char]) =>
    val p = take(i)
    val actual: Option[Text] = maybeP(p)(fromStrict(fromChars(chars)))
    val expected = if (i > chars.length) None else Some(new Text(chars.take(i)))
    actual must be_==(expected)
  }

  "takeWhile" ! check {(w: Char, chars: Array[Char]) =>
    val (h, t) = Text.fromChars(chars).span(_ == w)
    val actual: PTextResult[Text] = defP(takeWhile(_ == w))(fromStrict(fromChars(chars)))
    actual match {
      case Done(leftOver, res) => {
        (leftOver.toStrict mustEqual(t)) and (res.headOption.getOrElse(Text.empty) mustEqual(h))
      }
      case _ => failure
    }
    success
  }


  "skip" should {
    "skip the given character" in {
      val text = Text.fromChars("aabcabcdefc")
      val result = maybeP(skip(_ == 'a').flatMap(_ => takeRest))(fromStrict(text))
      result must be_==(Some(List(Text.fromChars("abcabcdefc"))))
    }
    "return none if text does not start with the given character" in {
      val text = Text.fromChars("baaacabcdefc")
      val result = maybeP(skip(_ == 'a').flatMap(_ => takeRest))(fromStrict(text))
      result must be_==(None)
    }

    "skip while starting with the given character" in {
      val text = Text.fromChars("aaaabcabcdefc")
      val result = maybeP(skipWhile(_ == 'a').flatMap(_ => takeRest))(fromStrict(text))
      result must be_==(Some(List(Text.fromChars("bcabcdefc"))))
    }
  }
//  "takeWhile" should {
//    "take while the predicate holds" in {
//      val text = Text.fromChars("aabbbcabcdefc")
//      val (h1, t1) = text.span(c => c == 'a' || c == 'b')
//      val result = defP(takeWhile(c => c == 'a' || c == 'b'))(fromStrict(text))
//      result match {
//        case Done(h, t) => (h.toStrict mustEqual(t1)) and (t mustEqual(h1))
//        case _ => failure
//      }
//      success
//    }
//    "return input if condition does not hold" in {
//      val text = Text.fromChars("aabbbcabcdefc")
//      val result = defP(takeWhile(c => c == 'b' ))(fromStrict(text))
//      result match {
//        case Done(h, t) => (h.toStrict mustEqual(text)) and (t must beEmpty)
//        case _ => failure("should return Done")
//      }
//    }
//  }
}
