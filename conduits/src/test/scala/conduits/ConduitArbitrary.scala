package conduits

import binary.ByteString

import org.scalacheck.{Gen, Arbitrary}
import java.nio.charset.Charset
import text.{Encoding, Text}
import Encoding._

/**
 * User: arjan
 */

object ConduitArbitrary {

   implicit def ByteStringArbitrary: Arbitrary[ByteString] = Arbitrary(Arbitrary.arbitrary[Array[Byte]].map(new ByteString(_)))

   implicit def textArbitrary: Arbitrary[Text] = Arbitrary(Arbitrary.arbitrary[Array[Char]].map(new Text(_)))

   implicit def charsetArbitrary: Arbitrary[Charset] = Arbitrary(Gen.oneOf(UTF8, UTF16, UTF16LE, UTF16BE))
}
