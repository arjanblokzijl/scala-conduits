package conduits
package text


/**
 * User: arjan
 */

class TextSpec extends FileSpecification {
  val ti = Text.textInstance
  "a bytestring" should {
    "reading a file twice should be equal" in {
      val bs1 = Text.readFile(test1).unsafePerformIO
      val bs2 = Text.readFile(test1).unsafePerformIO
      bs1.isEmpty must beFalse
      bs2.isEmpty must beFalse
      ti.equal(bs1, bs2)
    }
    "reading different files should not be equal" in {
      val bs1 = Text.readFile(test1).unsafePerformIO
      val bs2 = Text.readFile(test2).unsafePerformIO
      bs1.isEmpty must beFalse
      bs2.isEmpty must beFalse
      !ti.equal(bs1, bs2)
    }
  }
}
