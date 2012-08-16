package bs

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck

class FingerTreeByteStringSpec extends Specification with ScalaCheck {

  "take" ! check {
    (i: Int, s: Array[Byte]) =>
      FingerTreeByteString(s).take(i).toArray must be_==(s take i)
  }
  "drop" ! check {
    (i: Int, s: Array[Byte]) =>
      FingerTreeByteString(s).drop(i).toArray must be_==(s drop i)
  }
  "span" ! check {
    (b: Byte, s: Array[Byte]) =>
      val (l, r) = FingerTreeByteString(s).span(_ != b)
      val (l1, r1) = s span(_ != b)
      l.toArray must be_==(l1)
      r.toArray must be_==(r1)
  }
  "takeWhile" ! check {
    (b: Byte, s: Array[Byte]) =>
      FingerTreeByteString(s).takeWhile(_ != b).toArray must be_==(s.takeWhile(_ != b))
  }
  "dropWhile" ! check {
    (b: Byte, s: Array[Byte]) =>
      FingerTreeByteString(s).dropWhile(_ != b).toArray must be_==(s.dropWhile(_ != b))
  }
  "append" ! check {
    (a1: Array[Byte], a2: Array[Byte]) =>
      (FingerTreeByteString(a1) ++ FingerTreeByteString(a2)).toArray must be_==(a1 ++ a2)
  }
}
