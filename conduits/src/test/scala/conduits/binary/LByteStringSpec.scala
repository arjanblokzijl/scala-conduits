package conduits
package binary

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import ConduitArbitrary._
import collection.immutable.Stream

class LByteStringSpec extends Specification with ScalaCheck {

  val lbi = lbyteString.lbyteStringInstance
  "unpack folowing pack must be equal" ! check {(s: Stream[Byte]) =>
    lbyteString.pack(s).unpack must be_==(s)
  }
  "takeWhile" ! check {(s: Stream[Byte]) =>
    val actual = lbyteString.pack(s).takeWhile(_ > 20)
    actual.unpack must be_==(s.takeWhile(_ > 20))
  }
  "dropWhile" ! check {(s: Stream[Byte]) =>
    val expected: Stream[Byte] = s.dropWhile(_ < 50)
    lbyteString.pack(s).dropWhile(_ < 50).unpack must be_==(expected)
  }
  "head" ! check {(s: Stream[Byte]) =>
    lbyteString.pack(s).head must be_==(s.headOption)
  }
  "tail" ! check {(s: Stream[Byte]) =>
    lbyteString.pack(s).tail.map(_.unpack) must be_==(if (s.isEmpty) None else Some(s.tail))
  }
  "take" ! check {(s: Stream[Byte]) =>
    lbyteString.pack(s).take(10).unpack must be_==(s.take(10))
  }

  "lazy bytestring" should {
    "handle infinite streams" in {
      val s = Stream.from(1).map(_.toByte)
      lbyteString.pack(s).take(20).unpack must be_==(s.take(20))
    }
  }
}
