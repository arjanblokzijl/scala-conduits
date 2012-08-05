package bs

import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import scalaz.effect.IO
import IO._
import scala.io.Codec
import collection.mutable.{ArrayBuilder, Builder}
import collection.{Traversable, IndexedSeqOptimized}
import collection.generic.CanBuildFrom
import scalaz.std.anyVal
import ByteString._
import java.io.{FileOutputStream, FileInputStream, File}
import scalaz.{CharSet, Show, Order, Monoid}
import resourcet.IOUtils._
import java.util.Date

/**
 * A strict ByteString, which stores [[java.lang.Byte]]'s in an Array.
 */
abstract class ByteString(bytes: Array[Byte]) extends IndexedSeq[Byte] with IndexedSeqOptimized[Byte, ByteString] {
  private val arr = bytes.clone
  override protected[this] def newBuilder: Builder[Byte, ByteString] = ArrayBuilder.make[Byte]().mapResult(ByteString(_))

  def &:(b: Byte): ByteString = cons(b, this)

  def uncons: Option[(Byte, ByteString)] = if (isEmpty) None else Some(arr.head, ByteString(arr.tail))

  final def apply(idx: Int) = arr(idx)

  final val length = arr.length

  def bsMap(f: Byte => Byte): ByteString = ByteString(arr.map(f))

  override def foldRight[B](z: B)(f: (Byte, B) => B): B = {
    import scala.collection.mutable.ArrayStack
    val s = new ArrayStack[Byte]
    arr.foreach(a => s += a)
    var r = z
    while (!s.isEmpty) {
      // force and copy the value of r to ensure correctness
      val w = r
      r = f(s.pop, w)
    }
    r
  }

  def append(that: ByteString): ByteString = ByteString(arr ++ that.toArray)

  def toByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray).asReadOnlyBuffer

  def toArray: Array[Byte] = arr

  override def toString = new String(bytes)

  /**
   * Writes the contents of the this ByteString into the given ByteChannel.
   */
  def writeContents(os: FileOutputStream): IO[Unit] =
    if (isEmpty) IO(())
    else withFile(os)(s => IO(s.getChannel.write(toByteBuffer)) flatMap(_ => IO(())))

  /**
   * Writes the contents of the this ByteString into the given File.
   */
  def writeFile(f: File): IO[Unit] = writeContents(new FileOutputStream(f))
}


trait ByteStringInstances {

  import ByteString._

  implicit val byteStringInstance: Monoid[ByteString] with Order[ByteString] with Show[ByteString] = new Monoid[ByteString] with Order[ByteString] with Show[ByteString] {
    def show(f: ByteString) = f.toString.toList

    def append(f1: ByteString, f2: => ByteString) = ByteString(f1.toArray ++ f2.toArray)

    def zero: ByteString = empty

    def order(x: ByteString, y: ByteString): scalaz.Ordering = {
      val i1 = x.iterator
      val i2 = y.iterator
      while (i1.hasNext && i2.hasNext) {
        val a1 = i1.next()
        val a2 = i2.next()
        val o = if (a1 < a2) scalaz.Ordering.LT
        else if (a1 > a2) scalaz.Ordering.GT
        else scalaz.Ordering.EQ
        if (o != scalaz.Ordering.EQ) {
          return o
        }
      }
      anyVal.booleanInstance.order(i1.hasNext, i2.hasNext)
    }

    override def equalIsNatural: Boolean = true
  }
}

trait ByteStringFunctions {
  val DefaultChunkSize = 8*1024
  def apply(arr: Array[Byte]) = new ByteString(arr){}

  /** Converts a `java.nio.ByteBuffer` into a `ByteString`. */
  def fromByteBuffer(bytes: java.nio.ByteBuffer, size: Int = DefaultChunkSize): ByteString = {
    bytes.rewind()
    val ar = new Array[Byte](size)
    bytes.get(ar)
    ByteString(ar)
  }

  def fromString(s: String): ByteString = ByteString(s.getBytes(CharSet.UTF8))

  def fromSeq(s: Seq[Byte]): ByteString = ByteString(s.toArray)

  def cons(b: Byte, bs: ByteString): ByteString = ByteString(b +: bs.toArray)

  def readFile(f: File, chunkSize: Int = DefaultChunkSize): IO[ByteString] =
    IO(new FileInputStream(f).getChannel) flatMap(getContents(_, chunkSize))

  def getContents(chan: ByteChannel, capacity: Int = DefaultChunkSize): IO[ByteString] = {
    val buf = java.nio.ByteBuffer.allocate(capacity)
    IO(chan.read(buf)).map(i => i match {
      case -1 => empty
      case n => fromByteBuffer(buf, n)
    })
  }

  def writeContents(chan: ByteChannel, bs: ByteString): IO[Int] = {
    val buf = java.nio.ByteBuffer.allocate(bs.size)
    IO{
      buf.put(bs.toByteBuffer)
      buf.flip()
      chan.write(buf)
    }
  }

  def empty: ByteString = ByteString(Array.empty[Byte])

  def singleton(b: Byte): ByteString = ByteString(Array(b))

  def concat(bss: Stream[ByteString]): ByteString =
    bss.foldLeft[ByteString](empty)((bs1, bs2) => bs1.append(bs2))

}

object ByteString extends ByteStringInstances with ByteStringFunctions
