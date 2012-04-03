package conduits
package binary

import org.specs2.mutable.Specification
import scalaz._
import std.anyVal._
import resourcet._
import resource._


import CL._
import Binary._

import java.net.URL
import java.nio.ByteBuffer
import java.io.{FileInputStream, File}
import Conduits._

class BinarySpec extends Specification {
  import lbyteString._
  lazy val r: URL = this.getClass.getClassLoader.getResource("random")
  lazy val r1: URL = this.getClass.getClassLoader.getResource("test.txt")
  lazy val r2: URL = this.getClass.getClassLoader.getResource("test2.txt")
  val expectedBuf = java.nio.ByteBuffer.allocate(byteString.DefaultChunkSize)
  def random = new File(r.toURI)
  def f1 = new File(r1.toURI)
  def f2 = new File(r2.toURI)

  "binary" should {
    "stream a file in a Source" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random) %%== consume).unsafePerformIO
      val result = lbyteString.fromChunks(bss)
      val expected = lbyteString.readFile(random).unsafePerformIO
      lbyteString.lbyteStringInstance.equal(result, expected)
    }
    "stream a file in a Source in multiple chunks" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random, 8) %%== consume).unsafePerformIO
      val result = lbyteString.fromChunks(bss)
      val expected = lbyteString.readFile(random).unsafePerformIO
      lbyteString.lbyteStringInstance.equal(result, expected)
    }
    //TODO create separate LByteString test
    "check equality" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](f1) %%== consume).unsafePerformIO
      val result = lbyteString.fromChunks(bss)
      val expected = lbyteString.readFile(f2).unsafePerformIO
      !lbyteString.lbyteStringInstance.equal(result, expected)
    }
  }
}
