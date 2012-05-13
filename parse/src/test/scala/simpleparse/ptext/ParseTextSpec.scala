package simpleparse
package ptext

import org.specs2.mutable.Specification
import text.{Text, LText}
import simpleparse.ptext.StrictPText._
import simpleparse.ptext.LazyPText._
import LText._
import Text._
import org.specs2.ScalaCheck

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
}
