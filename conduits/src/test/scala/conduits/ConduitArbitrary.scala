package conduits

import bs.ByteString

import org.scalacheck.{Gen, Arbitrary}
import text._

/**
 * User: arjan
 */

object ConduitArbitrary {

   implicit def ByteStringArbitrary: Arbitrary[ByteString] = Arbitrary(Arbitrary.arbitrary[Array[Byte]].map(ByteString(_)))

   implicit def textArbitrary: Arbitrary[Text] = Arbitrary(Arbitrary.arbitrary[Array[Char]].map(new Text(_)))

//   implicit def charsetArbitrary: Arbitrary[Charset] = Arbitrary(Gen.oneOf(UTF8, UTF16))
}
