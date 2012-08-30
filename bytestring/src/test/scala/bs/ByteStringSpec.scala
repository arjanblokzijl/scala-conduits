package bs

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck

class ByteStringSpec extends FileSpecification with ScalaCheck {

  "take" ! check {
    (i: Int, s: Array[Byte]) =>
      ByteString(s).take(i).toArray must be_==(s take i)
  }
  "drop" ! check {
    (i: Int, s: Array[Byte]) =>
      ByteString(s).drop(i).toArray must be_==(s drop i)
  }
  "span single array" ! check {
    (b: Byte, s: Array[Byte]) =>
      val (l, r) = ByteString(s).span(_ != b)
      val (l1, r1) = s span(_ != b)
      l.toArray must be_==(l1)
      r.toArray must be_==(r1)
  }

  "span mutliple arrays" ! check {
    (b: Byte, arr: Array[Byte]) =>
      val (l, r) = (ByteString(arr) ++ ByteString(arr)).span(_ != b)
      val (l1, r1) = (arr ++ arr) span(_ != b)
      l.toArray must be_==(l1)
      r.toArray must be_==(r1)
  }

  "takeWhile" ! check {
    (b: Byte, s: Array[Byte]) =>
      ByteString(s).takeWhile(_ != b).toArray must be_==(s.takeWhile(_ != b))
  }
  "dropWhile" ! check {
    (b: Byte, s: Array[Byte]) =>
      ByteString(s).dropWhile(_ != b).toArray must be_==(s.dropWhile(_ != b))
  }
  "append" ! check {
    (a1: Array[Byte], a2: Array[Byte]) =>
      (ByteString(a1) ++ ByteString(a2)).toArray must be_==(a1 ++ a2)
  }

  "foldRight" ! check {
    (a1: Array[Byte]) =>
      ByteString(a1).foldRight(0)(_+_) must be_==(a1.foldRight(0)(_+_))
  }

  "foldLeft" ! check {
    (a1: Array[Byte]) =>
      ByteString(a1).foldLeft(0)(_+_) must be_==(a1.foldLeft(0)(_+_))
  }

  "zip" ! check {
    (a1: Array[Byte], a2: Array[Byte]) =>
      ByteString(a1).zip(ByteString(a2)).toArray must be_==((a1 zip a2))
  }

  val bsi = ByteString.byteStringInstance
  "a bytestring" should {
    "reading a file twice should be equal" in {
      val bs1 = ByteString.readFile(test1).unsafePerformIO
      val bs2 = ByteString.readFile(test1).unsafePerformIO
      bs1.isEmpty must beFalse
      bs2.isEmpty must beFalse
      bsi.equal(bs1, bs2)
    }
    "reading different files should not be equal" in {
      val bs1 = ByteString.readFile(test1).unsafePerformIO
      val bs2 = ByteString.readFile(test2).unsafePerformIO
      bs1.isEmpty must beFalse
      bs2.isEmpty must beFalse
      !bsi.equal(bs1, bs2)
    }
    "foldRight does not blow the stack" in {
      val res = ByteString(Array.range(0, 10000000).map(b => 1.toByte)).foldRight(0)(_+_)
      res must be_==(10000000)
    }
  }
}
