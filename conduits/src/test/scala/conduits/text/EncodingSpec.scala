package conduits
package text

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import conduits.ConduitArbitrary._
import Encoding._
import java.nio.charset.Charset


class EncodingSpec extends Specification with ScalaCheck {
  val ti = Text.textInstance

  "UTF8 encoding and decoding text equals" ! check {(t: Text) =>
     val res = decodeUtf8(encodeUtf8(t))
     ti.equal(t, res)
  }
}
