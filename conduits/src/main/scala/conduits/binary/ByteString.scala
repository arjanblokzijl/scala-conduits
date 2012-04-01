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

/**
 * User: arjan
 */
trait ByteString extends IndexedSeq[Byte]

final class SByteString(bytes: Array[Byte]) extends ByteString with IndexedSeqOptimized[Byte, ByteString] {
  import byteString._
  private val arr = bytes.clone
  override protected[this] def newBuilder: Builder[Byte, ByteString] = ArrayBuilder.make[Byte]().mapResult(new SByteString(_))

  def apply(idx: Int) = arr(idx)

  def length = arr.length

  def toByteBuffer: ByteBuffer = ByteBuffer.wrap(arr).asReadOnlyBuffer

  def toArray: Array[Byte] = arr

  def cons(b: Byte): SByteString = singleton(b) append this

  def append(f2: => SByteString): SByteString = new SByteString(this.toArray ++ f2.toArray)
}

trait SByteStringInstances {
  import byteString._
  implicit val byteStringInstance: Monoid[SByteString] with Order[SByteString] with Show[SByteString] = new Monoid[SByteString] with Order[SByteString] with Show[SByteString]  {
    def show(f: SByteString) = f.toString.toList

    def append(f1: SByteString, f2: => SByteString) = f1 append f2

    def zero: SByteString = empty

    def order(x: SByteString, y: SByteString): scalaz.Ordering = {
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
  val DefaultBufferSize = 8*1024
  /** Converts a `java.nio.ByteBuffer` into a `ByteString`. */
  def fromByteBuffer(bytes: java.nio.ByteBuffer, length: Int = DefaultBufferSize): SByteString = {
    bytes.rewind()
    val bufSize = if (length < bytes.remaining) bytes.remaining else length
    val ar = new Array[Byte](bufSize)
    bytes.get(ar)
    new SByteString(ar)
  }

  def readFile(f: File): IO[SByteString] =
    IO(new FileInputStream(f).getChannel) flatMap(getContents(_))

  def getContents(chan: ByteChannel, capacity: Int = DefaultBufferSize): IO[SByteString] = {
    val buf = java.nio.ByteBuffer.allocate(capacity)
    IO(chan.read(buf)).map(i => i match {
      case -1 => empty
      case n => fromByteBuffer(buf, n)
    })
  }

  def empty: SByteString = new SByteString(Array.empty[Byte])
  def singleton(b: Byte): SByteString = new SByteString(Array(b))
}

object byteString extends SByteStringInstances with SByteStringFunctions

