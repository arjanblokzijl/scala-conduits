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
import java.nio.{CharBuffer, ByteBuffer}
import java.io.{FileInputStream, File}

//TODO this is all very clumsy, perhaps take the approach in scalaz-nio branch
//https://github.com/jsuereth/scalaz/blob/scalaz-nio2
class BinarySpec extends Specification with ScalaCheck {
  lazy val r: URL = this.getClass.getClassLoader.getResource("test.txt")
  def decoder = scala.io.Codec.UTF8.newDecoder();
  val expectedBuf = java.nio.ByteBuffer.allocate(bufferSize)
  def f = new File(r.toURI)

  "binary" should {
    "stream a file in a Source" in {
      val result: Stream[ByteBuffer] = runResourceT(sourceFile[RTIO](f) >>== consume).unsafePerformIO
      val buf: ByteBuffer = result.head
      buf.rewind
      new FileInputStream(f).getChannel.read(expectedBuf)
      expectedBuf.rewind
      decoder.decode(buf) mustEqual(decoder.decode(expectedBuf))
    }
  }
}
