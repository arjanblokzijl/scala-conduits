package conduits

import binary.ByteString

import org.scalacheck.{Gen, Arbitrary}
import text.Text

/**
 * User: arjan
 */

object ConduitArbitrary {

   implicit def ByteStringArbitrary: Arbitrary[ByteString] = Arbitrary(Arbitrary.arbitrary[Array[Byte]].map(new ByteString(_)))

   implicit def textArbitrary: Arbitrary[Text] = Arbitrary(Arbitrary.arbitrary[Array[Char]].map(new Text(_)))
}
