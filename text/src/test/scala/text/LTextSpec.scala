package text

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import text.{LText => L}

/**
 * User: arjan
 */

class LTextSpec extends Specification with ScalaCheck {
  val lti = L.lTextStringInstance

  "unpack folowing pack must be equal" ! check {
    (s: Stream[Char]) =>
      L.pack(s).unpack must be_==(s)
  }
  "head" ! check {
    (s: Stream[Char]) =>
      L.pack(s).head must be_==(s.headOption)
  }
  "tail" ! check {
    (s: Stream[Char]) =>
      L.pack(s).tailOption.map(_.unpack) must be_==(if (s.isEmpty) None else Some(s.tail))
  }

  "take" ! check {
    (i: Int, s: Stream[Char]) =>
      L.pack(s).take(i).unpack must be_==(s.take(i))
  }
  "takeWhile" ! check {
    (i: Int, s: Stream[Char]) =>
      val actual = L.pack(s).takeWhile(_ > i)
      actual.unpack must be_==(s.takeWhile(_ > i))
  }

  "dropWhile" ! check {
    (i: Int, s: Stream[Char]) =>
      val expected: Stream[Char] = s.dropWhile(_ < i)
      L.pack(s).dropWhile(_ < i).unpack must be_==(expected)
  }
  "map" ! check {
    (s: Stream[Char]) =>
      val res = L.pack(s).map(c => (c + 1).toChar)
      res.unpack must be_==(s.map(b => (b + 1).toChar))
  }
  "lazy text" should {
    "handle infinite streams" in {
      val s = Stream.from(1).map(_.toChar)
      L.pack(s).take(20).unpack must be_==(s.take(20))
    }
  }
}
