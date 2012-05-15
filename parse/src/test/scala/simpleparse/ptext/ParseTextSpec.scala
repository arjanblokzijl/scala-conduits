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
  "single char" ! check {(c: Char, chars: Array[Char]) =>
    val p = char(c)
    val actual = maybeP(p)(fromStrict(fromChars(chars)))
    val expected = if (!chars.isEmpty && chars.head == c) Some(chars.head) else None
    actual must be_==(expected)
  }

  "any char" ! check {(chars: Array[Char]) =>
    val p = anyChar
    val actual = maybeP(p)(fromStrict(fromChars(chars)))
    val expected = if (chars.isEmpty) None else Some(chars.head)
    actual must be_==(expected)
  }

  "string" ! check {(chars: Array[Char]) =>
    val s = new Text(chars)
    val p = string(s)
    val actual: Option[Text] = maybeP(p)(fromStrict(fromChars(chars)))
    actual must be_==(Some(s))
  }

  "take" ! check {(i: Int, chars: Array[Char]) =>
    val actual: Option[Text] = maybeP(take(i))(fromStrict(fromChars(chars)))
    val expected = if (i > chars.length) None else Some(new Text(chars.take(i)))
    actual must be_==(expected)
  }

  "skip" ! check {(w: Char, chars: Array[Char]) =>
    val actual = maybeP(skip(_ == w).flatMap(_ => takeRest))(fromStrict(fromChars(chars)))
    val expected = if (chars.isEmpty || chars.head != w) None else Some(new Text(chars.tail))
    actual must be_==(expected)
  }

  "skipWhile" ! check {(w: Char, chars: Array[Char]) =>
    val actual = defP(skipWhile(_ <= w))(fromStrict(fromChars(chars)))
    val expected = new Text(chars.dropWhile(_ <= w))
    actual match {
      case Done(rest, ()) => rest.toStrict mustEqual(expected)
      case _ => failure("must return Done")
    }
  }

  "takeWhile" ! check {(w: Char, chars: Array[Char]) =>
    val (h, t) = Text.fromChars(chars).span(_ == w)
    val actual: PTextResult[Text] = defP(takeWhile(_ == w))(fromStrict(fromChars(chars)))
    actual match {
      case Done(rest, res) => {
        (rest.toStrict mustEqual(t)) and (res.headOption.getOrElse(Text.empty) mustEqual(h))
      }
      case _ => failure("must return Done")
    }
  }
  "endOfInput" ! check {(chars: Array[Char]) =>
    val t = Text.fromChars(chars)
    val actual = maybeP(endOfInput)(fromStrict(fromChars(chars)))
    if (t.isEmpty) actual must be_==(Some(()))
    else actual must beNone
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
  "takeWhile" should {
    "take while the predicate holds" in {
      val text = Text.fromChars("aabbbcabcdefc")
      val (h1, t1) = text.span(c => c == 'a' || c == 'b')
      val result = defP(takeWhile(c => c == 'a' || c == 'b'))(fromStrict(text))
      result match {
        case Done(h, t) => (h.toStrict mustEqual(t1)) and (t mustEqual(h1))
        case _ => failure("should return Done")
      }
      success
    }
    "return input if condition does not hold" in {
      val text = Text.fromChars("aabbbcabcdefc")
      val result = defP(takeWhile(c => c == 'b' ))(fromStrict(text))
      result match {
        case Done(h, t) => (h.toStrict mustEqual(text)) and (t must beEmpty)
        case _ => failure("should return Done")
      }
    }
  }

  "string" should {
    "fail if text does not contain string" in  {
      val text = Text.fromChars("aabbbcabcdefc")
      val s = Text.fromChars("abbb")
      val res = maybeP(string(s))(fromStrict(text))
      res must beNone
    }
    "succeed if text does contain string" in  {
      val text = Text.fromChars("aabbbbcabcdefc")
      val s = Text.fromChars("aabbb")
      defP(string(s))(fromStrict(text)) match {
        case Done(leftover, res) => (res mustEqual(s)) and (leftover.toStrict mustEqual(Text.fromChars("bcabcdefc")))
        case _ => failure("")
      }
    }
  }
}
