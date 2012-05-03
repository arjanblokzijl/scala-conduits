package text

import collection.IndexedSeqOptimized
import collection.mutable.{ArrayBuilder, Builder}
import scalaz.{Show, Order, Monoid}
import scalaz.std.anyVal

import Text._
import scalaz.effect.IO
import scalaz.effect.IO._
import java.io.{FileInputStream, File}
import java.nio.channels.ByteChannel
import java.nio.CharBuffer

/**
 *  A strict Text instance, which stores [[scala.Char]]'s in an Array.
 */
final class Text(chars: Array[Char]) extends IndexedSeq[Char] with IndexedSeqOptimized[Char, Text] {
  private val arr = chars.clone
  override protected[this] def newBuilder: Builder[Char, Text] = ArrayBuilder.make[Char]().mapResult(new Text(_))

  def uncons: Option[(Char, Text)] = if (isEmpty) None else Some(arr.head, new Text(arr.tail))

  def apply(idx: Int) = arr(idx)

  def length = arr.length

  def toArray: Array[Char] = arr

  def toCharBuffer: CharBuffer = CharBuffer.wrap(arr).asReadOnlyBuffer

  def append(that: Text): Text = new Text(arr ++ that.toArray)

  def unpack: String = new String(arr)

  def &:(c: Char): Text = cons(c, this)

  override def toString: String = new String(toArray)
}

trait TextFunctions {
  val DefaultChunkSize: Int = 8*1024
  def empty: Text = new Text(Array.empty[Char])

  def singleton(c: Char): Text = new Text(Array(c))

  def concat(tss: Stream[Text]): Text =
    tss.foldLeft[Text](empty)((t1, t2) => t1 append t2)

  def pack(s: String) = new Text(s.toCharArray)

  def cons(c: Char, tx: Text): Text = new Text(c +: tx.toArray)

  def readFile(f: File, chunkSize: Int = DefaultChunkSize): IO[Text] =
    IO(new FileInputStream(f).getChannel) flatMap(getContents(_, chunkSize))

  def getContents(chan: ByteChannel, capacity: Int = DefaultChunkSize): IO[Text] = {
    val buf = java.nio.ByteBuffer.allocate(capacity)
    IO(chan.read(buf)).map(i => i match {
      case -1 => empty
      case n => fromByteBuffer(buf, n)
    })
  }

  def fromSeq(s: Seq[Char]): Text = new Text(s.toArray)

  def fromIterator(i: Iterator[Char]): Text = new Text(i.toArray)

  def fromByteBuffer(bytes: java.nio.ByteBuffer, size: Int = DefaultChunkSize): Text = {
    bytes.rewind()
    val ar = new Array[Char](size)
    bytes.asCharBuffer.get(ar)
    new Text(ar)
  }

  def fromCharBuffer(chars: java.nio.CharBuffer): Text = {
    chars.rewind()
    val ar = new Array[Char](chars.remaining)
    chars.get(ar, 0, chars.remaining)
    new Text(ar)
  }

}

trait TextInstances {

  import Text._

  implicit val textInstance: Monoid[Text] with Order[Text] with Show[Text] = new Monoid[Text] with Order[Text] with Show[Text] {
    def show(f: Text) = f.toString.toList

    def append(f1: Text, f2: => Text) = new Text(f1.toArray ++ f2.toArray)

    def zero: Text = empty

    def order(x: Text, y: Text): scalaz.Ordering = {
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


object Text extends TextFunctions with TextInstances