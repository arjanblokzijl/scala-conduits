package conduits
package binary

import org.specs2.mutable.Specification
import scalaz._
import std.anyVal._
import resourcet._
import resource._

import empty.Void
import CL._
import Binary._
import bs._

import Conduits._
import org.specs2.ScalaCheck
import ConduitArbitrary._
import collection.immutable.Stream


class BinarySpec extends FileSpecification with ScalaCheck {
  import LByteString._
  val lbsi = lbyteStringInstance
  val sbsi = ByteString.byteStringInstance


  "binary head" ! check { (bs: ByteString) =>
    val res = sourceList[Id, ByteString](Stream(bs)) %%== Binary.head
    bs.headOption mustEqual(res)
  }

  "binary isolate" ! check { (n: Int, s: Stream[Byte]) =>
    val bstr = s.map(i => new ByteString(Array(i.toByte)))
    val bss = sourceList[Id, ByteString](bstr) %= Binary.isolate(n) %%== consume
    val result = ByteString.concat(bss)
    sbsi.equal(result, ByteString.concat(bstr.take(n)))
  }

  "binary takeWhile" ! check { (bs: ByteString) =>
    val bss = sourceList[Id, ByteString](Stream(bs)) %%== Binary.takeWhile[Id](b => b >= 10) =% consume
    val result = LByteString.fromChunks(bss)
    lbsi.equal(result, LByteString.fromChunks(Stream(bs)).takeWhile(b => b >= 10))
  }

  "binary dropWhile" ! check { (bs: ByteString) =>
    val bss = sourceList[Id, ByteString](Stream(bs)) %%== Binary.dropWhile[Id](b => b <= 10).flatMap(_ => consume)
    val result = LByteString.fromChunks(bss)
    lbsi.equal(result, LByteString.fromChunks(Stream(bs)).dropWhile(b => b <= 10))
  }

  "binary lines" ! check { (s: String) =>
    val actual = sourceList[Id, ByteString](Stream(ByteString.fromString(s))) %%== Binary.lines[Id] =% consume
    val expected = s.lines.toStream.map(ByteString.fromString)
    actual must be_== (expected)
  }

  "binary" should {
    "stream a file in a Source" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random) %%== consume).unsafePerformIO
      val result = LByteString.fromChunks(bss)
      val expected = LByteString.readFile(random).unsafePerformIO
      lbsi.equal(result, expected)
    }
    "stream a file in a Source in multiple chunks" in {
      val bss: Stream[ByteString] = runResourceT(sourceFile[RTIO](random, 8) %%== consume).unsafePerformIO
      val result = LByteString.fromChunks(bss)
      val expected = LByteString.readFile(random).unsafePerformIO
      lbsi.equal(result, expected)
    }
    "stream a file in a Source in multiple chunks" in {
      val tmp = tmpFile
      runResourceT(sourceFile[RTIO](random) %%== sinkFile(tmp)).unsafePerformIO
      val bs1 = ByteString.readFile(random).unsafePerformIO
      val bs2 = ByteString.readFile(tmp).unsafePerformIO
      sbsi.equal(bs1, bs2)
    }
    "read range" in {
      val tmp = tmpFile
      ByteString.fromString("0123456789").writeFile(tmp).unsafePerformIO
      val bss = runResourceT(sourceFileRange[RTIO](tmp, Some(2), Some(4)) %%== consume).unsafePerformIO
      val result = ByteString.concat(bss)
      sbsi.equal(result, ByteString.fromString("2345"))
    }
    "lines" in {
      val s: String = "01234\n5678\n9"
      val actual: Stream[ByteString] = sourceList[Id, ByteString](Stream(ByteString.fromString(s))) %%== Binary.lines[Id] =% consume
      val expected = s.lines.toStream.map(ByteString.fromString)
      actual must be_==(expected)
    }
  }
}
