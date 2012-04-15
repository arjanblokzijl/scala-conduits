package conduits
package text


/**
 * User: arjan
 */

class TextSpec extends FileSpecification {
  val ti = Text.textInstance
  "a bytestring" should {
    "reading a file twice should be equal" in {
      val t1 = Text.readFile(test1).unsafePerformIO
      val t2 = Text.readFile(test1).unsafePerformIO
      t1.isEmpty must beFalse
      t2.isEmpty must beFalse
      ti.equal(t1, t2)
    }
    "reading different files should not be equal" in {
      val t1 = Text.readFile(test1).unsafePerformIO
      val t2 = Text.readFile(test2).unsafePerformIO
      t1.isEmpty must beFalse
      t2.isEmpty must beFalse
      !ti.equal(t1, t2)
    }
  }
}
