package simpleparse
package ptext

import org.specs2.mutable.Specification
import text.{Text, LText}
import simpleparse.ptext.StrictPText._
import simpleparse.ptext.LazyPText._
import LText._
import Text._

/**
 * User: arjan
 */

class ParseTextSpec extends Specification {
  "parsing text" should {
    "match single character" in {
      val p = char('c')
      val res = maybeP(p)(fromStrict(fromChars("cdefc")))
      res mustEqual(Some('c'))
    }
    "fail if string does not start with character" in {
      val p = char('c')
      maybeP(p).apply(fromStrict(fromChars("dcefc"))) mustEqual(None)
    }
  }
}
