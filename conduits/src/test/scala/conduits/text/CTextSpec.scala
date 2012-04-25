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
import collection.Iterator
import collection.immutable.Stream
import ConduitArbitrary._
import binary.{LByteString, Char8, Binary, ByteString}

class CTextSpec extends Specification with ScalaCheck {

  "encode decode UTF8" ! check {(chars: Array[Char]) =>
    val t = new Text(chars)
    val res = CL.sourceList[IO, ByteString](Stream(encodeUtf8(t))) %= CText.decode[IO](Utf8) %%== CL.consume[IO, Text]
    LText.fromChunks(res.unsafePerformIO).unpack must be_==(t.toStream)
  }

  "encode decode UTF16_LE" ! check {(chars: Array[Char]) =>
    val t = new Text(chars)
    val res = CL.sourceList[IO, ByteString](Stream(encodeUtf16Le(t))) %= CText.decode[IO](Utf16_le) %%== CL.consume[IO, Text]
    LText.fromChunks(res.unsafePerformIO).unpack must be_==(t.toStream)
  }

  "encode decode UTF16_BE" ! check {(chars: Array[Char]) =>
    val t = new Text(chars)
    val res = CL.sourceList[IO, ByteString](Stream(encodeUtf16Be(t))) %= CText.decode[IO](Utf16_be) %%== CL.consume[IO, Text]
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
    "encode decode ASCII" in {
      val chars = new String((-127 to 127).map(_.toChar).toArray)
      val bs = Char8.pack(chars)
      val res = CL.sourceList[IO, ByteString](Stream(bs)) %= CText.decode[IO](Ascii) %%== CL.consume[IO, Text]
      LText.fromChunks(res.unsafePerformIO).unpack must be_==(chars.toStream)
    }
    "is lazy" in {
      val t = Text.pack("abcdefg")
      val bs = Encoding.encodeUtf8(t)
      def from(bs: => ByteString, bs2: => ByteString): Stream[ByteString] = Stream.cons(bs, from(bs, bs2))
      val actual: IO[Option[Text]] = sourceList[IO, ByteString](from(bs, sys.error("ignore"))) %%== CText.decode[IO](Utf8) =% CL.head[IO, Text]
      actual.unsafePerformIO.map(_.toString) must be_==(Some("abcdefg"))
    }
  }
  "split lines" should {
    "multiple lines in single string" in {
      val s: String = "01234\n5678\n9"
      val actual: Stream[Text] = sourceList[Id, Text](Stream(Text.pack(s))) %%== CText.lines[Id] =% consume
      val expected = s.lines.map(Text.pack).toStream
      actual must be_==(expected)
    }
    "split items" in {
      val s: String = "0123\n4\n5\n678\n9"
      val texts = Stream(Text.pack("0123\n4"), Text.pack("5\n"), Text.pack("678\n9"))
      val actual: Stream[Text] = sourceList[Id, Text](texts) %%== CText.lines[Id] =% consume
      val expected = s.lines.map(Text.pack).toStream
      actual must be_==(expected)
    }
    "line ending at the end" in {
      val s: String = "012345678\n"
      val actual: Stream[Text] = sourceList[Id, Text](Stream(Text.pack(s))) %%== CText.lines[Id] =% consume
      actual must have length 1
      actual.head.toString must be_==("012345678")
    }

//    it "is not too eager" $ do
//        x <- CL.sourceList ["foobarbaz", error "ignore me"] C.$$ CT.decode CT.utf8 C.=$ CL.head
//        x @?= Just "foobarbaz"
  }
}
