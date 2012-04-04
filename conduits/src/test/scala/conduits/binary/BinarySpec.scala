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
    val bsi = lbyteStringInstance
    "stream a file in a Source" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random) %%== consume).unsafePerformIO
      val result = lbyteString.fromChunks(bss)
      val expected = lbyteString.readFile(random).unsafePerformIO
      bsi.equal(result, expected)
    }
    "stream a file in a Source in multiple chunks" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random, 8) %%== consume).unsafePerformIO
      val result = lbyteString.fromChunks(bss)
      val expected = lbyteString.readFile(random).unsafePerformIO
      bsi.equal(result, expected)
    }
  }
}
