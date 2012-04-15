package conduits
package text

import org.specs2.mutable.Specification
import binary.ByteString
import scalaz._
import effect.IO
import effect.IO._
import Encoding._
import Conduits._
import resourcet.MonadThrow._

class CTextSpec extends Specification {

  "lazy text" should {
    "simple string in single chunk" in {
      val t = Text.pack("abcdefg")
      val bs = Encoding.encodeUtf8(t)
      val res = CL.sourceList[IO, ByteString](Stream(bs)) %= CText.decode[IO](Utf8) %%== CL.consume[IO, Text]
      val ltext: LText = LText.fromChunks(res.unsafePerformIO)
      ltext.unpack must be_==(t.toStream)
    }
  }
}
