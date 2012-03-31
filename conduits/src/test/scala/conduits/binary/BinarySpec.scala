package conduits
package binary

import org.specs2.mutable.Specification
import scalaz._
import std.anyVal._
import resourcet._
import resource._

import CL._
import Binary._

import org.specs2.ScalaCheck
import java.net.URL
import java.nio.ByteBuffer
import java.io.{FileInputStream, File}
import Conduits._

class BinarySpec extends Specification with ScalaCheck {
  lazy val r: URL = this.getClass.getClassLoader.getResource("random")
  val expectedBuf = java.nio.ByteBuffer.allocate(byteString.DefaultBufferSize)
  def f = new File(r.toURI)

  "binary" should {
    "stream a file in a Source" in {
      val result: Stream[SByteString] = runResourceT(sourceFile[RTIO](f) %%== consume).unsafePerformIO
      val actual = result.head
      val read = new FileInputStream(f).getChannel.read(expectedBuf)
      val expected = byteString.fromByteBuffer(expectedBuf, read)
      actual must be_==(expected)
    }
  }
}
