/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */

//Copy of Akka's bytestring implementation, used for benchmarking.
package conduits.benchmark

import java.nio.{ ByteBuffer, ByteOrder }

import scala.collection.IndexedSeqOptimized
import scala.collection.mutable.{ Builder, WrappedArray }
import scala.collection.immutable.{ IndexedSeq, VectorBuilder }
import scala.collection.generic.CanBuildFrom

object AkkaByteString {

  /**
   * Creates a new AkkaByteString by copying a byte array.
   */
  def apply(bytes: Array[Byte]): AkkaByteString = CompactAkkaByteString(bytes)

  /**
   * Creates a new AkkaByteString by copying bytes.
   */
  def apply(bytes: Byte*): AkkaByteString = CompactAkkaByteString(bytes: _*)

  /**
   * Creates a new AkkaByteString by converting from integral numbers to bytes.
   */
  def apply[T](bytes: T*)(implicit num: Integral[T]): AkkaByteString =
    CompactAkkaByteString(bytes: _*)(num)

  /**
   * Creates a new AkkaByteString by copying bytes from a ByteBuffer.
   */
  def apply(bytes: ByteBuffer): AkkaByteString = CompactAkkaByteString(bytes)

  /**
   * Creates a new AkkaByteString by encoding a String as UTF-8.
   */
  def apply(string: String): AkkaByteString = apply(string, "UTF-8")

  /**
   * Creates a new AkkaByteString by encoding a String with a charset.
   */
  def apply(string: String, charset: String): AkkaByteString = CompactAkkaByteString(string, charset)

  /**
   * Creates a new AkkaByteString by copying length bytes starting at offset from
   * an Array.
   */
  def fromArray(array: Array[Byte], offset: Int, length: Int): AkkaByteString =
    CompactAkkaByteString.fromArray(array, offset, length)

  val empty: AkkaByteString = CompactAkkaByteString(Array.empty[Byte])

  def newBuilder: AkkaByteStringBuilder = new AkkaByteStringBuilder

  implicit val canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, AkkaByteString] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, AkkaByteString] {
      def apply(ignore: TraversableOnce[Byte]): AkkaByteStringBuilder = newBuilder
      def apply(): AkkaByteStringBuilder = newBuilder
    }

  object AkkaByteString1C {
    def apply(bytes: Array[Byte]): AkkaByteString1C = new AkkaByteString1C(bytes)
  }

  /**
   * A compact (unsliced) and unfragmented AkkaByteString, implementation of AkkaByteString1C.
   */
  @SerialVersionUID(3956956327691936932L)
  final class AkkaByteString1C private (private val bytes: Array[Byte]) extends CompactAkkaByteString {
    def apply(idx: Int): Byte = bytes(idx)

    override def length: Int = bytes.length

    override def iterator: ByteIterator.ByteArrayIterator = ByteIterator.ByteArrayIterator(bytes, 0, bytes.length)

    def toAkkaByteString1: AkkaByteString1 = AkkaByteString1(bytes)

    def asByteBuffer: ByteBuffer =
      toAkkaByteString1.asByteBuffer

    def decodeString(charset: String): String = new String(bytes, charset)

    def ++(that: AkkaByteString): AkkaByteString =
      if (that.isEmpty) this
      else if (this.isEmpty) that
      else toAkkaByteString1 ++ that

    override def slice(from: Int, until: Int): AkkaByteString =
      if ((from != 0) || (until != length)) toAkkaByteString1.slice(from, until)
      else this
  }

  object AkkaByteString1 {
    def apply(bytes: Array[Byte]): AkkaByteString1 = new AkkaByteString1(bytes)
    def apply(bytes: Array[Byte], startIndex: Int, length: Int): AkkaByteString1 =
      new AkkaByteString1(bytes, startIndex, length)
  }

  /**
   * An unfragmented AkkaByteString.
   */
  final class AkkaByteString1 private (private val bytes: Array[Byte], private val startIndex: Int, val length: Int) extends AkkaByteString {

    private def this(bytes: Array[Byte]) = this(bytes, 0, bytes.length)

    def apply(idx: Int): Byte = bytes(checkRangeConvert(idx))

    override def iterator: ByteIterator.ByteArrayIterator =
      ByteIterator.ByteArrayIterator(bytes, startIndex, startIndex + length)

    private def checkRangeConvert(index: Int): Int = {
      if (0 <= index && length > index)
        index + startIndex
      else
        throw new IndexOutOfBoundsException(index.toString)
    }

    def isCompact: Boolean = (length == bytes.length)

    def compact: CompactAkkaByteString =
      if (isCompact) AkkaByteString1C(bytes) else AkkaByteString1C(toArray)

    def asByteBuffer: ByteBuffer = {
      val buffer = ByteBuffer.wrap(bytes, startIndex, length).asReadOnlyBuffer
      if (buffer.remaining < bytes.length) buffer.slice
      else buffer
    }

    def decodeString(charset: String): String =
      new String(if (length == bytes.length) bytes else toArray, charset)

    def ++(that: AkkaByteString): AkkaByteString = {
      if (that.isEmpty) this
      else if (this.isEmpty) that
      else that match {
        case b: AkkaByteString1C ⇒ AkkaByteStrings(this, b.toAkkaByteString1)
        case b: AkkaByteString1 ⇒
          if ((bytes eq b.bytes) && (startIndex + length == b.startIndex))
            new AkkaByteString1(bytes, startIndex, length + b.length)
          else AkkaByteStrings(this, b)
        case bs: AkkaByteStrings ⇒ AkkaByteStrings(this, bs)
      }
    }
  }

  object AkkaByteStrings {
    def apply(AkkaByteStrings: Vector[AkkaByteString1]): AkkaByteString = new AkkaByteStrings(AkkaByteStrings, (0 /: AkkaByteStrings)(_ + _.length))

    def apply(AkkaByteStrings: Vector[AkkaByteString1], length: Int): AkkaByteString = new AkkaByteStrings(AkkaByteStrings, length)

    def apply(b1: AkkaByteString1, b2: AkkaByteString1): AkkaByteString = compare(b1, b2) match {
      case 3 ⇒ new AkkaByteStrings(Vector(b1, b2), b1.length + b2.length)
      case 2 ⇒ b2
      case 1 ⇒ b1
      case 0 ⇒ AkkaByteString.empty
    }

    def apply(b: AkkaByteString1, bs: AkkaByteStrings): AkkaByteString = compare(b, bs) match {
      case 3 ⇒ new AkkaByteStrings(b +: bs.byteStrings, bs.length + b.length)
      case 2 ⇒ bs
      case 1 ⇒ b
      case 0 ⇒ AkkaByteString.empty
    }

    def apply(bs: AkkaByteStrings, b: AkkaByteString1): AkkaByteString = compare(bs, b) match {
      case 3 ⇒ new AkkaByteStrings(bs.byteStrings :+ b, bs.length + b.length)
      case 2 ⇒ b
      case 1 ⇒ bs
      case 0 ⇒ AkkaByteString.empty
    }

    def apply(bs1: AkkaByteStrings, bs2: AkkaByteStrings): AkkaByteString = compare(bs1, bs2) match {
      case 3 ⇒ new AkkaByteStrings(bs1.byteStrings ++ bs2.byteStrings, bs1.length + bs2.length)
      case 2 ⇒ bs2
      case 1 ⇒ bs1
      case 0 ⇒ AkkaByteString.empty
    }

    // 0: both empty, 1: 2nd empty, 2: 1st empty, 3: neither empty
    def compare(b1: AkkaByteString, b2: AkkaByteString): Int =
      if (b1.isEmpty)
        if (b2.isEmpty) 0 else 2
      else if (b2.isEmpty) 1 else 3

  }

  /**
   * A AkkaByteString with 2 or more fragments.
   */
  final class AkkaByteStrings (val byteStrings: Vector[AkkaByteString1], val length: Int) extends AkkaByteString {
    if (byteStrings.isEmpty) throw new IllegalArgumentException("AkkaByteStrings must not be empty")

    def apply(idx: Int): Byte =
      if (0 <= idx && idx < length) {
        var pos = 0
        var seen = 0
        while (idx >= seen + byteStrings(pos).length) {
          seen += byteStrings(pos).length
          pos += 1
        }
        byteStrings(pos)(idx - seen)
      } else throw new IndexOutOfBoundsException(idx.toString)

    override def iterator: ByteIterator.MultiByteArrayIterator =
      ByteIterator.MultiByteArrayIterator(byteStrings.toStream map { _.iterator })

    def ++(that: AkkaByteString): AkkaByteString = {
      if (that.isEmpty) this
      else if (this.isEmpty) that
      else that match {
        case b: AkkaByteString1C => AkkaByteStrings(this, b.toAkkaByteString1)
        case b: AkkaByteString1  => AkkaByteStrings(this, b)
        case bs: AkkaByteStrings => AkkaByteStrings(this, bs)
      }
    }

    def isCompact: Boolean = if (byteStrings.length == 1) byteStrings.head.isCompact else false

    def compact: CompactAkkaByteString = {
      if (isCompact) byteStrings.head.compact
      else {
        val ar = new Array[Byte](length)
        var pos = 0
        byteStrings foreach { b ⇒
          b.copyToArray(ar, pos, b.length)
          pos += b.length
        }
        AkkaByteString1C(ar)
      }
    }

    def asByteBuffer: ByteBuffer = compact.asByteBuffer

    def decodeString(charset: String): String = compact.decodeString(charset)
  }

}

/**
 * A [[http://en.wikipedia.org/wiki/Rope_(computer_science) Rope-like]] immutable
 * data structure containing bytes. The goal of this structure is to reduce
 * copying of arrays when concatenating and slicing sequences of bytes, and also
 * providing a thread safe way of working with bytes.
 *
 * TODO: Add performance characteristics
 */
sealed abstract class AkkaByteString extends IndexedSeq[Byte] with IndexedSeqOptimized[Byte, AkkaByteString] {
  def apply(idx: Int): Byte

  override protected[this] def newBuilder: AkkaByteStringBuilder = AkkaByteString.newBuilder

  // *must* be overridden by derived classes. This construction is necessary
  // to specialize the return type, as the method is already implemented in
  // a parent trait.
  override def iterator: ByteIterator = throw new UnsupportedOperationException("Method iterator is not implemented in AkkaByteString")

  override def head: Byte = apply(0)
  override def tail: AkkaByteString = drop(1)
  override def last: Byte = apply(length - 1)
  override def init: AkkaByteString = dropRight(1)

  override def slice(from: Int, until: Int): AkkaByteString =
    if ((from == 0) && (until == length)) this
    else iterator.slice(from, until).toAkkaByteString

  override def take(n: Int): AkkaByteString = slice(0, n)
  override def takeRight(n: Int): AkkaByteString = slice(length - n, length)
  override def drop(n: Int): AkkaByteString = slice(n, length)
  override def dropRight(n: Int): AkkaByteString = slice(0, length - n)

  override def takeWhile(p: Byte ⇒ Boolean): AkkaByteString = iterator.takeWhile(p).toAkkaByteString
  override def dropWhile(p: Byte ⇒ Boolean): AkkaByteString = iterator.dropWhile(p).toAkkaByteString
  override def span(p: Byte ⇒ Boolean): (AkkaByteString, AkkaByteString) =
    { val (a, b) = iterator.span(p); (a.toAkkaByteString, b.toAkkaByteString) }

  override def splitAt(n: Int): (AkkaByteString, AkkaByteString) = (take(n), drop(n))

  override def indexWhere(p: Byte ⇒ Boolean): Int = iterator.indexWhere(p)
  override def indexOf[B >: Byte](elem: B): Int = iterator.indexOf(elem)

  override def toArray[B >: Byte](implicit arg0: ClassManifest[B]): Array[B] = iterator.toArray
  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit =
    iterator.copyToArray(xs, start, len)

  override def foreach[@specialized U](f: Byte ⇒ U): Unit = iterator foreach f

  /**
   * Efficiently concatenate another AkkaByteString.
   */
  def ++(that: AkkaByteString): AkkaByteString

  /**
   * Copy as many bytes as possible to a ByteBuffer, starting from it's
   * current position. This method will not overflow the buffer.
   *
   * @param buffer a ByteBuffer to copy bytes to
   * @return the number of bytes actually copied
   */
  def copyToBuffer(buffer: ByteBuffer): Int = iterator.copyToBuffer(buffer)

  /**
   * Create a new AkkaByteString with all contents compacted into a single,
   * full byte array.
   * If isCompact returns true, compact is an O(1) operation, but
   * might return a different object with an optimized implementation.
   */
  def compact: CompactAkkaByteString

  /**
   * Check whether this AkkaByteString is compact in memory.
   * If the AkkaByteString is compact, it might, however, not be represented
   * by an object that takes full advantage of that fact. Use compact to
   * get such an object.
   */
  def isCompact: Boolean

  /**
   * Returns a read-only ByteBuffer that directly wraps this AkkaByteString
   * if it is not fragmented.
   */
  def asByteBuffer: ByteBuffer

  /**
   * Creates a new ByteBuffer with a copy of all bytes contained in this
   * AkkaByteString.
   */
  def toByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray)

  /**
   * Decodes this AkkaByteString as a UTF-8 encoded String.
   */
  final def utf8String: String = decodeString("UTF-8")

  /**
   * Decodes this AkkaByteString using a charset to produce a String.
   */
  def decodeString(charset: String): String

  /**
   * map method that will automatically cast Int back into Byte.
   */
  final def mapI(f: Byte ⇒ Int): AkkaByteString = map(f andThen (_.toByte))
}

object CompactAkkaByteString {
  /**
   * Creates a new CompactAkkaByteString by copying a byte array.
   */
  def apply(bytes: Array[Byte]): CompactAkkaByteString = {
    if (bytes.isEmpty) empty
    else AkkaByteString.AkkaByteString1C(bytes.clone)
  }

  /**
   * Creates a new CompactAkkaByteString by copying bytes.
   */
  def apply(bytes: Byte*): CompactAkkaByteString = {
    if (bytes.isEmpty) empty
    else {
      val ar = new Array[Byte](bytes.size)
      bytes.copyToArray(ar)
      CompactAkkaByteString(ar)
    }
  }

  /**
   * Creates a new CompactAkkaByteString by converting from integral numbers to bytes.
   */
  def apply[T](bytes: T*)(implicit num: Integral[T]): CompactAkkaByteString = {
    if (bytes.isEmpty) empty
    else AkkaByteString.AkkaByteString1C(bytes.map(x ⇒ num.toInt(x).toByte)(collection.breakOut))
  }

  /**
   * Creates a new CompactAkkaByteString by copying bytes from a ByteBuffer.
   */
  def apply(bytes: ByteBuffer): CompactAkkaByteString = {
    if (bytes.remaining < 1) empty
    else {
      val ar = new Array[Byte](bytes.remaining)
      bytes.get(ar)
      AkkaByteString.AkkaByteString1C(ar)
    }
  }

  /**
   * Creates a new CompactAkkaByteString by encoding a String as UTF-8.
   */
  def apply(string: String): CompactAkkaByteString = apply(string, "UTF-8")

  /**
   * Creates a new CompactAkkaByteString by encoding a String with a charset.
   */
  def apply(string: String, charset: String): CompactAkkaByteString = {
    if (string.isEmpty) empty
    else AkkaByteString.AkkaByteString1C(string.getBytes(charset))
  }

  /**
   * Creates a new CompactAkkaByteString by copying length bytes starting at offset from
   * an Array.
   */
  def fromArray(array: Array[Byte], offset: Int, length: Int): CompactAkkaByteString = {
    val copyOffset = math.max(offset, 0)
    val copyLength = math.max(math.min(array.length - copyOffset, length), 0)
    if (copyLength == 0) empty
    else {
      val copyArray = new Array[Byte](copyLength)
      Array.copy(array, copyOffset, copyArray, 0, copyLength)
      AkkaByteString.AkkaByteString1C(copyArray)
    }
  }

  val empty: CompactAkkaByteString = AkkaByteString.AkkaByteString1C(Array.empty[Byte])
}

/**
 * A compact AkkaByteString.
 *
 * The AkkaByteString is guarantied to be contiguous in memory and to use only
 * as much memory as required for its contents.
 */
sealed abstract class CompactAkkaByteString extends AkkaByteString with Serializable {
  def isCompact: Boolean = true
  def compact: this.type = this
}

/**
 * A mutable builder for efficiently creating a [[akka.util.AkkaByteString]].
 *
 * The created AkkaByteString is not automatically compacted.
 */
final class AkkaByteStringBuilder extends Builder[Byte, AkkaByteString] {
  builder ⇒

  import AkkaByteString.{ AkkaByteString1C, AkkaByteString1, AkkaByteStrings }
  private var _length: Int = 0
  private val _builder: VectorBuilder[AkkaByteString1] = new VectorBuilder[AkkaByteString1]()
  private var _temp: Array[Byte] = _
  private var _tempLength: Int = 0
  private var _tempCapacity: Int = 0

  protected def fillArray(len: Int)(fill: (Array[Byte], Int) ⇒ Unit): this.type = {
    ensureTempSize(_tempLength + len)
    fill(_temp, _tempLength)
    _tempLength += len
    _length += len
    this
  }

  protected def fillByteBuffer(len: Int, byteOrder: ByteOrder)(fill: ByteBuffer ⇒ Unit): this.type = {
    fillArray(len) {
      case (array, start) ⇒
        val buffer = ByteBuffer.wrap(array, start, len)
        buffer.order(byteOrder)
        fill(buffer)
    }
  }

  def length: Int = _length

  override def sizeHint(len: Int): Unit = {
    resizeTemp(len - (_length - _tempLength))
  }

  private def clearTemp(): Unit = {
    if (_tempLength > 0) {
      val arr = new Array[Byte](_tempLength)
      Array.copy(_temp, 0, arr, 0, _tempLength)
      _builder += AkkaByteString1(arr)
      _tempLength = 0
    }
  }

  private def resizeTemp(size: Int): Unit = {
    val newtemp = new Array[Byte](size)
    if (_tempLength > 0) Array.copy(_temp, 0, newtemp, 0, _tempLength)
    _temp = newtemp
    _tempCapacity = _temp.length
  }

  private def ensureTempSize(size: Int): Unit = {
    if (_tempCapacity < size || _tempCapacity == 0) {
      var newSize = if (_tempCapacity == 0) 16 else _tempCapacity * 2
      while (newSize < size) newSize *= 2
      resizeTemp(newSize)
    }
  }

  def +=(elem: Byte): this.type = {
    ensureTempSize(_tempLength + 1)
    _temp(_tempLength) = elem
    _tempLength += 1
    _length += 1
    this
  }

  override def ++=(xs: TraversableOnce[Byte]): this.type = {
    xs match {
      case b: AkkaByteString1C ⇒
        clearTemp()
        _builder += b.toAkkaByteString1
        _length += b.length
      case b: AkkaByteString1 ⇒
        clearTemp()
        _builder += b
        _length += b.length
      case bs: AkkaByteStrings ⇒
        clearTemp()
        _builder ++= bs.byteStrings
        _length += bs.length
      case xs: WrappedArray.ofByte ⇒
        clearTemp()
        _builder += AkkaByteString1(xs.array.clone)
        _length += xs.length
      case seq: collection.IndexedSeq[_] ⇒
        ensureTempSize(_tempLength + xs.size)
        xs.copyToArray(_temp, _tempLength)
        _tempLength += seq.length
        _length += seq.length
      case _ ⇒
        super.++=(xs)
    }
    this
  }

  /**
   * Add a single Byte to this builder.
   */
  def putByte(x: Byte): this.type = this += x

  /**
   * Add a single Short to this builder.
   */
  def putShort(x: Int)(implicit byteOrder: ByteOrder): this.type = {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      this += (x >>> 8).toByte
      this += (x >>> 0).toByte
    } else if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
      this += (x >>> 0).toByte
      this += (x >>> 8).toByte
    } else throw new IllegalArgumentException("Unknown byte order " + byteOrder)
  }

  /**
   * Add a single Int to this builder.
   */
  def putInt(x: Int)(implicit byteOrder: ByteOrder): this.type = {
    fillArray(4) {
      case (target, offset) ⇒
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
          target(offset + 0) = (x >>> 24).toByte
          target(offset + 1) = (x >>> 16).toByte
          target(offset + 2) = (x >>> 8).toByte
          target(offset + 3) = (x >>> 0).toByte
        } else if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
          target(offset + 0) = (x >>> 0).toByte
          target(offset + 1) = (x >>> 8).toByte
          target(offset + 2) = (x >>> 16).toByte
          target(offset + 3) = (x >>> 24).toByte
        } else throw new IllegalArgumentException("Unknown byte order " + byteOrder)
    }
    this
  }

  /**
   * Add a single Long to this builder.
   */
  def putLong(x: Long)(implicit byteOrder: ByteOrder): this.type = {
    fillArray(8) {
      case (target, offset) ⇒
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
          target(offset + 0) = (x >>> 56).toByte
          target(offset + 1) = (x >>> 48).toByte
          target(offset + 2) = (x >>> 40).toByte
          target(offset + 3) = (x >>> 32).toByte
          target(offset + 4) = (x >>> 24).toByte
          target(offset + 5) = (x >>> 16).toByte
          target(offset + 6) = (x >>> 8).toByte
          target(offset + 7) = (x >>> 0).toByte
        } else if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
          target(offset + 0) = (x >>> 0).toByte
          target(offset + 1) = (x >>> 8).toByte
          target(offset + 2) = (x >>> 16).toByte
          target(offset + 3) = (x >>> 24).toByte
          target(offset + 4) = (x >>> 32).toByte
          target(offset + 5) = (x >>> 40).toByte
          target(offset + 6) = (x >>> 48).toByte
          target(offset + 7) = (x >>> 56).toByte
        } else throw new IllegalArgumentException("Unknown byte order " + byteOrder)
    }
    this
  }

  /**
   * Add a single Float to this builder.
   */
  def putFloat(x: Float)(implicit byteOrder: ByteOrder): this.type =
    putInt(java.lang.Float.floatToRawIntBits(x))(byteOrder)

  /**
   * Add a single Double to this builder.
   */
  def putDouble(x: Double)(implicit byteOrder: ByteOrder): this.type =
    putLong(java.lang.Double.doubleToRawLongBits(x))(byteOrder)

  /**
   * Add a number of Bytes from an array to this builder.
   */
  def putBytes(array: Array[Byte]): this.type =
    putBytes(array, 0, array.length)

  /**
   * Add a number of Bytes from an array to this builder.
   */
  def putBytes(array: Array[Byte], start: Int, len: Int): this.type =
    fillArray(len) { case (target, targetOffset) ⇒ Array.copy(array, start, target, targetOffset, len) }

  /**
   * Add a number of Shorts from an array to this builder.
   */
  def putShorts(array: Array[Short])(implicit byteOrder: ByteOrder): this.type =
    putShorts(array, 0, array.length)(byteOrder)

  /**
   * Add a number of Shorts from an array to this builder.
   */
  def putShorts(array: Array[Short], start: Int, len: Int)(implicit byteOrder: ByteOrder): this.type =
    fillByteBuffer(len * 2, byteOrder) { _.asShortBuffer.put(array, start, len) }

  /**
   * Add a number of Ints from an array to this builder.
   */
  def putInts(array: Array[Int])(implicit byteOrder: ByteOrder): this.type =
    putInts(array, 0, array.length)(byteOrder)

  /**
   * Add a number of Ints from an array to this builder.
   */
  def putInts(array: Array[Int], start: Int, len: Int)(implicit byteOrder: ByteOrder): this.type =
    fillByteBuffer(len * 4, byteOrder) { _.asIntBuffer.put(array, start, len) }

  /**
   * Add a number of Longs from an array to this builder.
   */
  def putLongs(array: Array[Long])(implicit byteOrder: ByteOrder): this.type =
    putLongs(array, 0, array.length)(byteOrder)

  /**
   * Add a number of Longs from an array to this builder.
   */
  def putLongs(array: Array[Long], start: Int, len: Int)(implicit byteOrder: ByteOrder): this.type =
    fillByteBuffer(len * 8, byteOrder) { _.asLongBuffer.put(array, start, len) }

  /**
   * Add a number of Floats from an array to this builder.
   */
  def putFloats(array: Array[Float])(implicit byteOrder: ByteOrder): this.type =
    putFloats(array, 0, array.length)(byteOrder)

  /**
   * Add a number of Floats from an array to this builder.
   */
  def putFloats(array: Array[Float], start: Int, len: Int)(implicit byteOrder: ByteOrder): this.type =
    fillByteBuffer(len * 4, byteOrder) { _.asFloatBuffer.put(array, start, len) }

  /**
   * Add a number of Doubles from an array to this builder.
   */
  def putDoubles(array: Array[Double])(implicit byteOrder: ByteOrder): this.type =
    putDoubles(array, 0, array.length)(byteOrder)

  /**
   * Add a number of Doubles from an array to this builder.
   */
  def putDoubles(array: Array[Double], start: Int, len: Int)(implicit byteOrder: ByteOrder): this.type =
    fillByteBuffer(len * 8, byteOrder) { _.asDoubleBuffer.put(array, start, len) }

  def clear(): Unit = {
    _builder.clear
    _length = 0
    _tempLength = 0
  }

  def result: AkkaByteString =
    if (_length == 0) AkkaByteString.empty
    else {
      clearTemp()
      val byteStrings = _builder.result
      if (byteStrings.size == 1)
        byteStrings.head
      else
        AkkaByteStrings(byteStrings, _length)
    }

  /**
   * Directly wraps this AkkaByteStringBuilder in an OutputStream. Write
   * operations on the stream are forwarded to the builder.
   */
  def asOutputStream: java.io.OutputStream = new java.io.OutputStream {
    def write(b: Int): Unit = builder += b.toByte

    override def write(b: Array[Byte], off: Int, len: Int): Unit = { builder.putBytes(b, off, len) }
  }
}
