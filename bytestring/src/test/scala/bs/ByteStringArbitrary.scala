package bs

import org.scalacheck.Arbitrary

/**
 * User: arjan
 */

object ByteStringArbitrary {
  implicit def ByteStringArbitrary: Arbitrary[ByteString] = Arbitrary(Arbitrary.arbitrary[Array[Byte]].map(new ByteString(_)))
}
