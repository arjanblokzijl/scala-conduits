package conduits
package text

import org.specs2.mutable.Specification
import scalaz._
import effect.IO
import effect.IO._
import Encoding._
import Conduits._
import resourcet.MonadThrow._
import org.specs2.ScalaCheck
import conduits.CL._
import binary.{Binary, ByteString}
import collection.Iterator
import collection.immutable.Stream

class CTextSpec extends Specification with ScalaCheck {

  "encode decode UTF8" ! check {(s: String) =>
    val t = new Text(s.getBytes(UTF8).map(_.toChar))
    val res = CL.sourceList[IO, ByteString](Stream(encodeUtf8(t))) %= CText.decode[IO](Utf8) %%== CL.consume[IO, Text]
    LText.fromChunks(res.unsafePerformIO).unpack must be_==(t.toStream)
  }

  "ctext lines" ! check { (s: String) =>
    val actual = sourceList[Id, Text](Stream(new Text(s.toCharArray))) %%== CText.lines[Id] =% consume
    val expected = s.lines.map(Text.pack).toStream
    actual must be_== (expected)
  }

  "CText text" should {
    "simple string in single chunk" in {
      val t = Text.pack("abcdefg")
      val bs = Encoding.encodeUtf8(t)
      val res = CL.sourceList[IO, ByteString](Stream(bs)) %= CText.decode[IO](Utf8) %%== CL.consume[IO, Text]
      val ltext: LText = LText.fromChunks(res.unsafePerformIO)
      ltext.unpack must be_==(t.toStream)
    }
    "encode decode in different charsets are not equal" in {
      val s: String = "abcdefghijk"
      val t = new Text(s.getBytes(UTF8).map(_.toChar))
      val t2 = new Text(s.getBytes(UTF16).map(_.toChar))
      val res = CL.sourceList[IO, ByteString](Stream(encodeUtf8(t2))) %= CText.decode[IO](Utf8) %%== CL.consume[IO, Text]
      LText.fromChunks(res.unsafePerformIO).unpack mustNotEqual(t.toStream)
    }
    "lines" in {
      val s: String = "01234\n5678\n9"
      val actual: Stream[Text] = sourceList[Id, Text](Stream(Text.pack(s))) %%== CText.lines[Id] =% consume
      val expected = s.lines.map(Text.pack).toStream
      actual must be_==(expected)
    }
  }
}
