package conduits
package text

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import conduits.ConduitArbitrary._
import Encoding._


class EncodingSpec extends Specification with ScalaCheck {
  val ti = Text.textInstance

  "encoding and decoding text equals" ! check {(t: Text) =>
     val res = decodeUtf8(encodeUtf8(t))
     ti.equal(t, res)
  }

  "encoding" should {
    "encoding and decoding text should be equal" in {
      val t = Text.pack("the quick brown fox")
      val bs = Encoding.encodeUtf8(t)
      val res = Encoding.decodeUtf8(bs)
      ti.equal(t, res)
    }
  }
}
