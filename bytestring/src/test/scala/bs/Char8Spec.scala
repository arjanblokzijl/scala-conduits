package bs

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck

/**
 * User: arjan
 */

class Char8Spec extends Specification with ScalaCheck {

  "pack and unpack are equal" ! check {
    (s: String) =>
      Char8.unpack(Char8.pack(s)) must be_==(s)
  }
}
