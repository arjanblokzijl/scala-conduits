package text

import java.nio.charset.Charset
import org.scalacheck.{Gen, Arbitrary}

/**
 * User: arjan
 */

object TextArbitrary {
  implicit def textArbitrary: Arbitrary[Text] = Arbitrary(Arbitrary.arbitrary[Array[Char]].map(new Text(_)))

//  implicit def charsetArbitrary: Arbitrary[Charset] = Arbitrary(Gen.oneOf(UTF8, UTF16))
}
