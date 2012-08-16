package bs

import scalaz.{UnitReducer, Reducer, FingerTree, syntax}
import collection.mutable.Builder
import collection.IndexedSeqOptimized

/**
 * User: arjan
 */

sealed trait FingerTreeByteString extends syntax.Ops[FingerTree[Int, Array[Byte]]] {
  import FingerTreeByteString._
  private def rangeError(i: Int) = sys.error("Index out of range: " + i + " >= " + self.measure)
  /**
    * Returns the byte at the given position. Throws an error if the index is out of range.
    * Time complexity: O(log N)
    */
  def apply(i: Int): Byte = {
    val (a, b) = self.split(_ > i)
    b.viewl.headOption.map(_(i - a.measure)).getOrElse(rangeError(i))
  }

  /**
   * Returns the number of characters in this `ByteString`.
   * Time complexity: O(1)
   */
  def length: Int = self.measure

  /**
   * Returns the number of characters in this `ByteString`.
   * Time complexity: O(1)
   */
  def size: Int = self.measure

  /**
   * Appends another `Cord` to the end of this one.
   * Time complexity: O(log (min N M)) where M and N are the lengths of the two `Cord`s.
   */
  def ++(xs: FingerTreeByteString): FingerTreeByteString = byteString(self <++> xs.self)

  /**
   * Appends a `byte arraz` to the end of this `Cord`.
   * Time complexity: O(1)
   */
  def :+(x: => Array[Byte]): FingerTreeByteString = byteString(self :+ x)

  /**
   * Prepends a `byte array` to the beginning of this `Cord`.
   * Time complexity: O(1)
   */
  def +:(x: => Array[Byte]): FingerTreeByteString = byteString(x +: self)

  /**
   * Prepends a `Byte` to the beginning of this `Cord`.
   * Time complexity: O(1)
   */
  def -:(x: => Byte): FingerTreeByteString = byteString(Array(x) +: self)

  /**
   * Appends a `Byte` to the end of this `Cord`.
   * Time complexity: O(1)
   */
  def :-(x: => Byte): FingerTreeByteString = byteString(self :+ Array(x))

  /**
   * Splits this `ByteString` in two at the given position.
   * Time complexity: O(log N)
   */
  def split(i: Int): (FingerTreeByteString, FingerTreeByteString) = {
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
  def drop(n: Int): FingerTreeByteString = split(n)._2

  def take(n: Int): FingerTreeByteString = split(n)._1

  def dropWhile(p: Byte => Boolean): FingerTreeByteString = drop(prefixLength(p))

  def takeWhile(p: Byte => Boolean): FingerTreeByteString = take(prefixLength(p))

  def span(p: Byte => Boolean): (FingerTreeByteString, FingerTreeByteString) =
    splitAt(prefixLength(p))


  def prefixLength(p: Byte => Boolean): Int = segmentLength(p, 0)

  def splitAt(n: Int): (FingerTreeByteString, FingerTreeByteString) = (take(n), drop(n))

  def segmentLength(p: Byte => Boolean, from: Int): Int = {
    val len = length
    var i = from
    while (i < len && p(this(i))) i += 1
    i - from
  }

  def toArray: Array[Byte] = self.toList.toArray.flatten //TODO optimize this

  override def toString = new String(toArray)
}

object FingerTreeByteString {
  import scalaz.std.anyVal._

  private def byteString[A](v: FingerTree[Int, Array[Byte]]): FingerTreeByteString = new FingerTreeByteString {
    val self = v
  }

  def empty[A]: FingerTreeByteString =
    byteString(FingerTree.empty[Int, Array[Byte]])

  def apply(ar: Array[Byte]): FingerTreeByteString =
    byteString(FingerTree.single[Int, Array[Byte]](ar)(sizer))

  implicit def sizer: Reducer[Array[Byte], Int] = UnitReducer((a: Array[Byte]) => a.length)
}