package conduits
package binary

import org.specs2.mutable.Specification
import java.net.URL
import java.io.File

/**
 * User: arjan
 */

trait FileSpecification extends Specification {
  lazy val r: URL = this.getClass.getClassLoader.getResource("random")
  lazy val r1: URL = this.getClass.getClassLoader.getResource("test.txt")
  lazy val r2: URL = this.getClass.getClassLoader.getResource("test2.txt")
  val expectedBuf = java.nio.ByteBuffer.allocate(ByteString.DefaultChunkSize)
  def random = new File(r.toURI)
  def test1: File = new File(r1.toURI)
  def test2: File = new File(r2.toURI)
  def tmpFile = File.createTempFile("conduit", "tmp")
}
