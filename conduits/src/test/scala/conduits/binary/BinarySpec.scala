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
import org.specs2.ScalaCheck
import ConduitArbitrary._
import collection.immutable.Stream
import conduits.pipes.Zero

class BinarySpec extends FileSpecification with ScalaCheck {
  import lbyteString._
  val lbsi = lbyteStringInstance
  val sbsi = byteString.byteStringInstance


  "binary head" ! check { (bs: ByteString) =>
    val res = sourceList[Id, ByteString](Stream(bs)) %%== Binary.head
    bs.headOption mustEqual(res)
  }

  "binary isolate" ! check { (n: Int, s: Stream[Byte]) =>
    val bstr = s.map(i => new ByteString(Array(i.toByte)))
    val bss = sourceList[Id, ByteString](bstr) %= Binary.isolate(n) %%== consume
    val result = byteString.concat(bss)
    sbsi.equal(result, byteString.concat(bstr.take(n)))
  }

  "binary takeWhile" ! check { (bs: ByteString) =>
    val bss = sourceList[Id, ByteString](Stream(bs)) %%== Binary.takeWhile[Id](b => b >= 10) =% consume
    val result = lbyteString.fromChunks(bss)
    lbsi.equal(result, lbyteString.fromChunks(Stream(bs)).takeWhile(b => b >= 10))
  }

  "binary dropWhile" ! check { (bs: ByteString) =>
    val bss = sourceList[Id, ByteString](Stream(bs)) %%== Binary.dropWhile[Id](b => b <= 10).flatMap(_ => consume)
    val result = lbyteString.fromChunks(bss)
    lbsi.equal(result, lbyteString.fromChunks(Stream(bs)).dropWhile(b => b <= 10))
  }

  "binary" should {
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
      byteString.fromString("0123456789").writeFile(tmp).unsafePerformIO
      val bss = runResourceT(sourceFileRange[RTIO](tmp, Some(2), Some(4)) %%== consume).unsafePerformIO
      val result = byteString.concat(bss)
      sbsi.equal(result, byteString.fromString("2345"))
    }
  }
}
