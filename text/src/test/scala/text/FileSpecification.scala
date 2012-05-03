package text

import org.specs2.mutable.Specification
import java.net.URL
import java.io.File

/**
 * User: arjan
 */

trait FileSpecification extends Specification {
  lazy val r1: URL = this.getClass.getClassLoader.getResource("test.txt")
  lazy val r2: URL = this.getClass.getClassLoader.getResource("test2.txt")

  def test1: File = new File(r1.toURI)

  def test2: File = new File(r2.toURI)

  def tmpFile = File.createTempFile("conduit", "tmp")
}
