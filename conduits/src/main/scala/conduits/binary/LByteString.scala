package conduits
package binary

import LByteString._
import scalaz.effect.IO
import IO._
import java.io.{FileInputStream, File}
import java.nio.channels.ByteChannel
import scalaz.{Show, Order, Monoid}
import collection.mutable.Builder
import collection.IndexedSeqOptimized

/**
 * A minimalistic version of a lazy ByteString.
 */
sealed trait LByteString extends IndexedSeq[Byte] with IndexedSeqOptimized[Byte, LByteString] {
  override protected[this] def newBuilder: Builder[Byte, LByteString] = new LByteStringBuilder
  import LByteString._
  def fold[Z](empty: => Z, chunk: (=> ByteString, => LByteString) => Z): Z

  def foldrChunks[A](z: => A)(f: (ByteString, => A) => A): A = {
    import scalaz.Free._
    import scalaz.std.function._
    def go(bs: => LByteString, z: => A, f: (ByteString, => A) => A): Trampoline[A] = bs match {
      case Empty() => return_(z)
      case Chunk(c, cs) => go(cs, z, f) map (x => f(c, x))
    }
    go(this, z, f).run
  }

  override def isEmpty: Boolean = this match {
    case Empty() => true
    case _ => false
  }

  /**
   * The 'cons' operation, pre-pending the given byte to this ByteString. This operation
   * creates a singleton byte array with the given byte as first element, and this bytestring instance as the rest of the chunks.
   */
  def #::(b: => Byte): LByteString = Chunk(ByteString.singleton(b), this)

  /**
   * Takes the given number of bytes from this bytestring.
   */
  override def take(n: Int): LByteString = this match {
    case Empty() => Empty.apply
    case Chunk(c, cs) =>
      if (n <= 0) Empty.apply
      else {
        val bs = c.take(n)
        if (bs.length < c.length) Chunk(bs, Empty.apply)
        else Chunk(bs, cs take (n - bs.length))
      }
  }

  /**
   * Gets the tail of this byteString, if it is not empty.
   */
  def tailOption: Option[LByteString] = this match {
    case Empty() => None
    case Chunk(c, cs) => if (c.length == 1 && !cs.isEmpty) Some(cs)
                         else if (c.length == 1 && c.isEmpty) None
                         else if (c.isEmpty && !cs.isEmpty) Some(cs)
                         else Some(Chunk(c.tail, cs))
  }

  def unpack: Stream[Byte] = this match {
    case Empty() => Stream.Empty
    case Chunk(c, cs) => c.toStream #::: cs.unpack
  }

  def toChunks: Stream[ByteString] = this match {
    case Empty() => Stream.Empty
    case Chunk(c, cs) => c #:: cs.toChunks
  }

  /**
   * A stricter version of cons, evaluating the first chunk to see whether it is more efficient to
   * coalesce the byte into this chunk rather than to create a new chunk by default.
   * @param b
   * @return
   */
  def conss(b: Byte): LByteString = fold(empty = singleton(b), chunk = (sb, lb) =>
    if (sb.length < 64) Chunk(b &: sb, lb) else Chunk(ByteString.singleton(b), lb)
  )

  def append(ys: => LByteString): LByteString = fold(empty = ys, chunk = (sb, lb) =>
    Chunk(sb, lb append ys)
  )

  def uncons: Option[(Byte, LByteString)] = this match {
    case Empty() => None
    case Chunk(c, cs) => Some(c.head, if (c.length == 1) cs else Chunk(c.tail, cs))
  }

  override def takeWhile(p: Byte => Boolean): LByteString = this match {
    case Empty() => Empty.apply
    case Chunk(c, cs) => {
      val bs = c takeWhile p
      if (bs.isEmpty) Empty.apply
      else if (bs.length == c.length) Chunk(c, cs takeWhile p)
      else Chunk(bs, Empty.apply)
    }
  }

  override def dropWhile(p: Byte => Boolean): LByteString = this match {
    case Empty() => Empty.apply
    case Chunk(c, cs) => {
      val bs = c dropWhile p
      if (bs.isEmpty) cs.dropWhile(p)
      else Chunk(bs, cs)
    }
  }

  def apply(idx: Int): Byte = this match {
    case Empty() => sys.error("apply on empty LByteString")
    case Chunk(c, cs) =>
      if (c.size > idx) c.apply(idx)
      else cs.apply(idx)
  }

  def length: Int = this match {
    case Empty() => 0
    case Chunk(c, cs) => c.length + cs.length
  }
}

object LByteString extends LByteStringFunctions with LByteStringInstances {

  object Empty {
    def apply = new LByteString {
      def fold[Z](empty: => Z, chunk: (=> ByteString, => LByteString) => Z): Z = empty
    }
    def unapply(bs: LByteString): Boolean = bs.fold(true, (_, _) => false)
  }

  object Chunk {
    def apply(bs: => ByteString, lbs: => LByteString) = new LByteString {
      def fold[Z](empty: => Z, chunk: (=> ByteString, => LByteString) => Z): Z = chunk(bs, lbs)
    }
    def unapply(bs: LByteString): Option[(ByteString, LByteString)] = bs.fold(None, (b, lb) => Some(b, lb))
  }
}

trait LByteStringFunctions {
  def empty: LByteString = Empty.apply

  def singleton(b: => Byte): LByteString = Chunk(ByteString.singleton(b), Empty.apply)

  def readFile(f: File, chunkSize: Int = ByteString.DefaultChunkSize): IO[LByteString] =
      IO(new FileInputStream(f).getChannel) flatMap(getContents(_, chunkSize))

  def getContents(chan: ByteChannel, capacity: Int = ByteString.DefaultChunkSize): IO[LByteString] = {
    def loop: IO[LByteString] = {
      ByteString.getContents(chan, capacity).flatMap((bs: ByteString) =>
        if (bs.isEmpty) IO(chan.close) flatMap(_ => IO(Empty.apply))
        else {
          for {
            cs <- loop.unsafeInterleaveIO
            lbs <- io(rw => cs.map(c => rw -> Chunk(bs, c)))
          } yield lbs
        })
    }
    loop
  }

  def fromChunks(s: => Stream[ByteString]): LByteString = s match {
    case Stream.Empty => Empty.apply
    case x #:: xs => if (x.isEmpty) fromChunks(xs)
                     else Chunk(x, fromChunks(xs))
  }

  def pack(s: => Stream[Byte], chunkSize: Int = ByteString.DefaultChunkSize): LByteString = s match {
      case Stream.Empty => Empty.apply
      case _ => {
        val (xs, xss) = s.splitAt(chunkSize)
        val head = new ByteString(xs.toArray)
        if (xss isEmpty) Chunk(head, Empty.apply)
        else Chunk(head, pack(xss))
      }
    }
}

trait LByteStringInstances {
  implicit val lbyteStringInstance: Monoid[LByteString] with Order[LByteString] with Show[LByteString] = new Monoid[LByteString] with Order[LByteString] with Show[LByteString]  {
    def show(f: LByteString) = f match {
      case Empty() => "<Empty>".toList
      case Chunk(c, cs) => ByteString.byteStringInstance.show(c) ::: show(cs)
    }

    def append(f1: LByteString, f2: => LByteString) = f1 append f2

    def zero: LByteString = LByteString.empty

    def order(xs: LByteString, ys: LByteString): scalaz.Ordering = (xs, ys) match {
      case (Empty(), Empty()) => scalaz.Ordering.EQ
      case (Empty(), Chunk(_, _)) => scalaz.Ordering.LT
      case (Chunk(_, _), Empty()) => scalaz.Ordering.GT
      case (Chunk(x, xs), Chunk(y, ys)) => {
        val so = ByteString.byteStringInstance.order(x, y)
        if (so == scalaz.Ordering.EQ) order(xs, ys)
        else so
      }
    }

    override def equalIsNatural: Boolean = true
  }
}

final class LByteStringBuilder extends scala.collection.mutable.LazyBuilder[Byte, LByteString] {
  def result() = pack(parts.toStream.flatMap(_ toStream))
}