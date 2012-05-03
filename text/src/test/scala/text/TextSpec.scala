package text

import java.net.URL
import java.io.File
import org.specs2.mutable.Specification

class TextSpec extends Specification {
  val ti = Text.textInstance
  lazy val r1: URL = this.getClass.getClassLoader.getResource("test.txt")
  lazy val r2: URL = this.getClass.getClassLoader.getResource("test2.txt")

  def test1: File = new File(r1.toURI)

  def test2: File = new File(r2.toURI)

  def tmpFile = File.createTempFile("conduit", "tmp")
  "text" should {
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
