package conduits
package binary

import org.specs2.mutable.Specification
import scalaz._
import std.anyVal._
import effect.IO
import effect.IO._
import scalaz.Id
import org.scalacheck.Arbitrary._
import resourcet._
import resource._

import CL._
import Binary._

import org.specs2.ScalaCheck
import java.net.URL
import java.nio.ByteBuffer
import java.io.{FileInputStream, File}

//TODO this is all very clumsy, perhaps take the approach in scalaz-nio branch
//https://github.com/jsuereth/scalaz/blob/scalaz-nio2
class BinarySpec extends Specification with ScalaCheck {
  lazy val r: URL = this.getClass.getClassLoader.getResource("test.txt")
  val expectedBuf = java.nio.ByteBuffer.allocate(byteString.DefaultBufferSize)
  def f = new File(r.toURI)

  "binary" should {
    "stream a file in a Source take2" in {
      val result: Stream[ByteString] = runResourceT(sourceFile[RTIO](f) >>== consume).unsafePerformIO
      val buf: ByteBuffer = result.head.asByteBuffer
      new FileInputStream(f).getChannel.read(expectedBuf)
      buf must be_==(byteString.fromByteBuffer(expectedBuf).asByteBuffer)
    }
  }
}
