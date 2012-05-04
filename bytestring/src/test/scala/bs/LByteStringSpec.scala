package bs

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import ByteStringArbitrary._
import collection.immutable.Stream

class LByteStringSpec extends Specification with ScalaCheck {

  val lbi = LByteString.lbyteStringInstance
  "unpack folowing pack must be equal" ! check {
    (s: Stream[Byte]) =>
      LByteString.pack(s).unpack must be_==(s)
  }
  "takeWhile" ! check {
    (s: Stream[Byte]) =>
      val actual = LByteString.pack(s).takeWhile(_ > 20)
      actual.unpack must be_==(s.takeWhile(_ > 20))
  }
  "dropWhile" ! check {
    (s: Stream[Byte]) =>
      val expected: Stream[Byte] = s.dropWhile(_ < 50)
      LByteString.pack(s).dropWhile(_ < 50).unpack must be_==(expected)
  }
  "head" ! check {
    (s: Stream[Byte]) =>
      LByteString.pack(s).head must be_==(s.headOption)
  }
  "tail" ! check {
    (s: Stream[Byte]) =>
      LByteString.pack(s).tailOption.map(_.unpack) must be_==(if (s.isEmpty) None else Some(s.tail))
  }
  "take" ! check {
    (s: Stream[Byte]) =>
      LByteString.pack(s).take(10).unpack must be_==(s.take(10))
  }
  "map" ! check {
    (s: Stream[Byte]) =>
      val res: LByteString = LByteString.pack(s).map(b => (b + 1).toByte)
      res.unpack must be_==(s.map(b => (b + 1).toByte))
  }

  "lazy bs" should {
    "handle infinite streams" in {
      val s = Stream.from(1).map(_.toByte)
      LByteString.pack(s).take(20).unpack must be_==(s.take(20))
    }
    "handle map lazy" in {
      val s = Stream.from(1).map(_.toByte)
      LByteString.pack(s).map(b => (b + 1).toByte).take(20).unpack must be_==(s.map(b => (b + 1).toByte).take(20))
    }
  }
}
