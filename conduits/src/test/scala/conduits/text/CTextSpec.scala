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
import org.specs2.ScalaCheck

class CTextSpec extends Specification with ScalaCheck {

  "encode decode UTF8" ! check {(s: String) =>
    val t = new Text(s.getBytes(UTF8).map(_.toChar))
    val res = CL.sourceList[IO, ByteString](Stream(encodeUtf8(t))) %= CText.decode[IO](Utf8) %%== CL.consume[IO, Text]
    LText.fromChunks(res.unsafePerformIO).unpack must be_==(t.toStream)
  }

  "CText text" should {
    "simple string in single chunk" in {
      val t = Text.pack("abcdefg")
      val bs = Encoding.encodeUtf8(t)
      val res = CL.sourceList[IO, ByteString](Stream(bs)) %= CText.decode[IO](Utf8) %%== CL.consume[IO, Text]
      val ltext: LText = LText.fromChunks(res.unsafePerformIO)
      ltext.unpack must be_==(t.toStream)
    }
  }
}
