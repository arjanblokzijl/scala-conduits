package conduits
package binary

import java.nio.ByteBuffer
import java.io.{FileInputStream, File}
import java.nio.channels.ByteChannel
import scalaz.effect.IO
import IO._
import scala.io.Codec
import collection.mutable.{ArrayBuilder, Builder}
import scalaz.{Show, Order, Monoid}
import collection.{Traversable, IndexedSeqOptimized}
import collection.generic.CanBuildFrom
import scalaz.std.anyVal
import byteString._

/**
 * User: arjan
 */

final class ByteString(bytes: Array[Byte]) extends IndexedSeq[Byte] with IndexedSeqOptimized[Byte, ByteString] {
  private val arr = bytes.clone
  override protected[this] def newBuilder: Builder[Byte, ByteString] = ArrayBuilder.make[Byte]().mapResult(new ByteString(_))

  def &:(b: Byte): ByteString = cons(b, this)

  def apply(idx: Int) = arr(idx)

  def length = arr.length

  def append(that: => ByteString): ByteString = new ByteString(arr ++ that.toArray)

  def toByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray).asReadOnlyBuffer

  def toArray: Array[Byte] = arr

  /**
   * Writes the contents of the this ByteString into the given ByteChannel.
   */
  def writeContents(chan: ByteChannel): IO[Unit] =
    if (isEmpty) IO(())
    else IO(chan.write(toByteBuffer)).flatMap(_ => IO(()))

}

trait SByteStringInstances {
  import byteString._
  implicit val byteStringInstance: Monoid[ByteString] with Order[ByteString] with Show[ByteString] = new Monoid[ByteString] with Order[ByteString] with Show[ByteString] {
    def show(f: ByteString) = f.toString.toList

    def append(f1: ByteString, f2: => ByteString) = new ByteString(f1.toArray ++ f2.toArray)

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

trait SByteStringFunctions {
  val DefaultChunkSize = 8*1024
  /** Converts a `java.nio.ByteBuffer` into a `ByteString`. */
  def fromByteBuffer(bytes: java.nio.ByteBuffer, size: Int = DefaultChunkSize): ByteString = {
    bytes.rewind()
    val ar = new Array[Byte](size)
    bytes.get(ar)
    new ByteString(ar)
  }

  def cons(b: Byte, bs: ByteString): ByteString = new ByteString(b +: bs.toArray)

  def readFile(f: File, chunkSize: Int = DefaultChunkSize): IO[ByteString] =
    IO(new FileInputStream(f).getChannel) flatMap(getContents(_, chunkSize))

  def getContents(chan: ByteChannel, capacity: Int = DefaultChunkSize): IO[ByteString] = {
    val buf = java.nio.ByteBuffer.allocate(capacity)
    IO(chan.read(buf)).map(i => i match {
      case -1 => empty
      case n => fromByteBuffer(buf, n)
    })
  }

  def empty: ByteString = new ByteString(Array.empty[Byte])
  def singleton(b: Byte): ByteString = new ByteString(Array(b))
}

object byteString extends SByteStringInstances with SByteStringFunctions

