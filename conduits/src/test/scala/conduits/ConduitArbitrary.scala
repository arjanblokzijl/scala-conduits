package conduits

import binary.ByteString

import org.scalacheck.{Gen, Arbitrary}
/**
 * User: arjan
 */

object ConduitArbitrary {

   implicit def ByteStringArbitrary: Arbitrary[ByteString] = Arbitrary(Arbitrary.arbitrary[Array[Byte]].map(new ByteString(_)))
}
