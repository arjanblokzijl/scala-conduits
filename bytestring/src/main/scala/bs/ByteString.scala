package bs

import scalaz._
import effect.IO
import java.io.{FileOutputStream, FileInputStream, File}
import java.nio.channels.{ByteChannel, ReadableByteChannel}
import ByteString._
import scalaz.syntax
import java.nio.ByteBuffer
import std.anyVal
import std.indexedSeq.indexedSeqMonoid
import resourcet.IOUtils._
import scalaz.Reducer._
import scalaz.UnitReducer
import bs.ByteString.apply
import scala.Some

sealed trait ByteString extends syntax.Ops[FingerTree[Int, Array[Byte]]] {

  private def rangeError(i: Int) = sys.error("Index out of range: " + i + " >= " + self.measure)
  /**
    * Returns the byte at the given position. Throws an error if the index is out of range.
    * Time complexity: O(log N)
    */
  final def apply(i: Int): Byte = {
    val (a, b) = self.split(_ > i)
    b.viewl.headOption.map(_(i - a.measure)).getOrElse(rangeError(i))
  }

  /**
   * Returns the number of characters in this `ByteString`.
   * Time complexity: O(1)
   */
  final def length: Int = self.measure

  final def isEmpty: Boolean = length == 0

  /**
   * Returns the number of characters in this `ByteString`.
   * Time complexity: O(1)
   */
  final def size: Int = self.measure

  /**
   * Appends another `Cord` to the end of this one.
   * Time complexity: O(log (min N M)) where M and N are the lengths of the two `Cord`s.
   */
  final def ++(xs: ByteString): ByteString = byteString(self <++> xs.self)

  /**
   * Appends a `byte arraz` to the end of this `Cord`.
   * Time complexity: O(1)
   */
  final def :+(x: => Array[Byte]): ByteString = byteString(self :+ x)

  /**
   * Prepends a `byte array` to the beginning of this `Cord`.
   * Time complexity: O(1)
   */
  final def +:(x: => Array[Byte]): ByteString = byteString(x +: self)

  /**
   * Prepends a `Byte` to the beginning of this `Cord`.
   * Time complexity: O(1)
   */
  final def -:(x: => Byte): ByteString = byteString(Array(x) +: self)

  /**
   * Appends a `Byte` to the end of this `Cord`.
   * Time complexity: O(1)
   */
  final def :-(x: => Byte): ByteString = byteString(self :+ Array(x))

  final def uncons: Option[(Byte, ByteString)] = if (isEmpty) None else Some(head, tail)

  final def map[B](f: Byte => Byte): ByteString = byteString(self map (_ map f))

  final def headOption: Option[Byte] = if (isEmpty) None else Some(head)

  final def tailOption: Option[ByteString] = if (isEmpty) None else Some(tail)

  final def head: Byte = self.head.head

  final def tail: ByteString = drop(1)

  final def zip(bs: ByteString): Stream[(Byte, Byte)] = zipWith(bs)((_, _))

  final def zipWith[A](bs: ByteString)(f: (Byte, Byte) => A): Stream[A] =
      if (isEmpty || bs.isEmpty) Stream.empty
      else f(head, bs.head) #:: tail.zipWith(bs.tail)(f)

  /**
   * Writes the contents of the this ByteString into the given ByteChannel.
  */
  final def writeContents(os: FileOutputStream): IO[Unit] =
    if (isEmpty) IO(())
    else withFile(os)(s => IO(s.getChannel.write(toByteBuffer)) flatMap(_ => IO(())))

  final def writeFile(f: File): IO[Unit] = writeContents(new FileOutputStream(f))


  /**
   * Splits this `ByteString` in two at the given position.
   * Time complexity: O(log N)
   */
  final def split(i: Int): (ByteString, ByteString) = {
    //sanity checking makes the actual implementation simpler
    if (i >= self.measure) (byteString(self), empty)
    else if (i <= 0) (empty, byteString(self))
    else {
      val (l, r) = self.split(_ > i)
      val (l1, r1) = r.viewl.headOption.map(_.splitAt(i - l.measure)).getOrElse(rangeError(i))

      val right = if (r.measure > self.measure - i) {
        val (_, r2) = r.viewl.tailOption.map(_.split(_ > r1.length)).getOrElse(rangeError(i))
        r1 +: r2
      } else r

      (byteString(l :+ l1), byteString(right))
    }
  }

  /**
   * Removes the first `n` characters from the front of this `ByteString`.
   * Time complexity: O(min N (N - n))
   */
  final def drop(n: Int): ByteString = split(n)._2

  final def take(n: Int): ByteString = split(n)._1

  final def dropWhile(p: Byte => Boolean): ByteString = drop(prefixLength(p))

  final def takeWhile(p: Byte => Boolean): ByteString = take(prefixLength(p))

  final def span(p: Byte => Boolean): (ByteString, ByteString) =
    splitAt(prefixLength(p))

  final def prefixLength(p: Byte => Boolean): Int = segmentLength(p, 0)

  final def splitAt(n: Int): (ByteString, ByteString) = (take(n), drop(n))

  final def segmentLength(p: Byte => Boolean, from: Int): Int = {
    val len = length
    var i = from
    while (i < len && p(this(i))) i += 1
    i - from
  }

  final def toStream: Stream[Byte] = self.toStream.flatMap(_.toStream)

  final def toArray: Array[Byte] = toList.toArray

  final def toList: List[Byte] = self.map(x => x)(ByteString.ListReducer).measure

  final def toByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray).asReadOnlyBuffer

  final def toIndexedSeq: IndexedSeq[Byte] = self.foldMap(_.toIndexedSeq : IndexedSeq[Byte])

  final override def toString = new String(toArray)

  final def ===(that: ByteString): Boolean = this.toList == that.toList

  final def foldLeft[A, B](z: => B)(f: (B, Byte) => B) =
    self.foldLeft(z)((b, arr) => arr.foldLeft(z)(f))

  final def foldRight[A, B](z: => B)(f: (Byte, => B) => B) = {
    def foldArr(z: B)(arr: Array[Byte]): B = {
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
    self.foldRight(z)((arr, b) => foldArr(z)(arr))
  }
}

trait ByteStringInstances {

  import ByteString._

  implicit val byteStringInstance: Monoid[ByteString] with Order[ByteString] with Show[ByteString] = new Monoid[ByteString] with Show[ByteString]  with Order[ByteString] {
//    def show(f: ByteString) = f.toString

    def append(f1: ByteString, f2: => ByteString) = ByteString(f1.toArray ++ f2.toArray)

    def zero: ByteString = empty

    import scalaz.std.list._
    import scalaz.std.anyVal._
    def order(x: ByteString, y: ByteString): scalaz.Ordering =
      Order[List[Byte]].order(x.toList, y.toList)

    override def equalIsNatural: Boolean = true
  }
}



trait ByteStringFunctions {
  import ByteString._
  val DefaultChunkSize = 8*1024

  /** Converts a `java.nio.ByteBuffer` into a `ByteString`. */
  def fromByteBuffer(bytes: java.nio.ByteBuffer, size: Int = DefaultChunkSize): ByteString = {
    bytes.rewind()
    val ar = new Array[Byte](size)
    bytes.get(ar)
    ByteString(ar)
  }

  /** Converts a `java.nio.ByteBuffer` into a `ByteString`. */
  def fromInputStream(stream: java.io.InputStream, size: Int = DefaultChunkSize): IO[ByteString] = {
    val chan = java.nio.channels.Channels.newChannel(stream)
    getContents(chan)
  }

  def fromString(s: String): ByteString = ByteString(s.getBytes(CharSet.UTF8))

  def fromSeq(s: Seq[Byte]): ByteString = ByteString(s.toArray)

  def cons(b: Byte, bs: ByteString): ByteString = ByteString(b +: bs.toArray)

  def readFile(f: File, chunkSize: Int = DefaultChunkSize): IO[ByteString] =
    IO(new FileInputStream(f).getChannel) flatMap(getContents(_, chunkSize))

  def getContents(chan: ReadableByteChannel, capacity: Int = DefaultChunkSize): IO[ByteString] = {
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

  def concat(bss: Stream[ByteString]): ByteString =
    bss.foldLeft[ByteString](empty)((bs1, bs2) => bs1 ++ bs2)

}

object ByteString extends ByteStringInstances with ByteStringFunctions {
  import scalaz.std.anyVal._

  private def byteString[A](v: FingerTree[Int, Array[Byte]]): ByteString = new ByteString {
    val self = v
  }

  def empty[A]: ByteString =
    byteString(FingerTree.empty[Int, Array[Byte]])

  def singleton(b: => Byte): ByteString = apply(Array(b))


  def apply(ar: Array[Byte]): ByteString =
    byteString(FingerTree.single[Int, Array[Byte]](ar)(sizer))

  implicit def sizer: Reducer[Array[Byte], Int] = UnitReducer((a: Array[Byte]) => a.length)

  import scalaz.std.list._
  def ListReducer: Reducer[Array[Byte], List[Byte]] = {
    unitConsReducer((b: Array[Byte]) => b.toList, c => c.toList ++ _)
  }

}