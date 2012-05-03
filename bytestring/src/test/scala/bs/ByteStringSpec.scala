package bs

/**
 * User: arjan
 */

class ByteStringSpec extends FileSpecification {
  val bsi = ByteString.byteStringInstance
  "a bs" should {
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
  }
}
