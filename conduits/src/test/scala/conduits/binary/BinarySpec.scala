package conduits
package binary

import org.specs2.mutable.Specification
import scalaz._
import std.anyVal._
import resourcet._
import resource._


import CL._
import Binary._

import Conduits._

class BinarySpec extends FileSpecification {
  import lbyteString._

  "binary" should {
    val lbsi = lbyteStringInstance
    val sbsi = byteString.byteStringInstance
    "stream a file in a Source" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random) %%== consume).unsafePerformIO
      val result = lbyteString.fromChunks(bss)
      val expected = lbyteString.readFile(random).unsafePerformIO
      lbsi.equal(result, expected)
    }
    "stream a file in a Source in multiple chunks" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random, 8) %%== consume).unsafePerformIO
      val result = lbyteString.fromChunks(bss)
      val expected = lbyteString.readFile(random).unsafePerformIO
      lbsi.equal(result, expected)
    }
    "stream a file in a Source in multiple chunks" in {
      val tmp = tmpFile
      runResourceT(sourceFile[RTIO](random) %%== sinkFile(tmp)).unsafePerformIO
      val bs1 = byteString.readFile(random).unsafePerformIO
      val bs2 = byteString.readFile(tmp).unsafePerformIO
      sbsi.equal(bs1, bs2)
    }
    "read range" in {
      val tmp = tmpFile
      val contents = byteString.fromString("0123456789").writeFile(tmp).unsafePerformIO
      val bss = runResourceT(sourceFileRange[RTIO](tmp, Some(2), Some(4)) %%== consume).unsafePerformIO
      val result = byteString.concat(bss)
      sbsi.equal(result, byteString.fromString("2345"))
    }
  }
}
