package conduits
package binary

/**
 * User: arjan
 */

class ByteStringSpec extends FileSpecification {
  val bsi = byteString.byteStringInstance
  "a bytestring" should {
    "reading a file twice should be equal" in {
      val bs1 = byteString.readFile(test1).unsafePerformIO
      val bs2 = byteString.readFile(test1).unsafePerformIO
      bs1.isEmpty must beFalse
      bs2.isEmpty must beFalse
      bsi.equal(bs1, bs2)
    }
    "reading different files should not be equal" in {
      val bs1 = byteString.readFile(test1).unsafePerformIO
      val bs2 = byteString.readFile(test2).unsafePerformIO
      bs1.isEmpty must beFalse
      bs2.isEmpty must beFalse
      !bsi.equal(bs1, bs2)
    }
  }
}
