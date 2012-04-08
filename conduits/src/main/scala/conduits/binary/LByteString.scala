package conduits
package binary

import LByteString._
import java.nio.ByteBuffer
import scalaz.effect.IO
import scalaz.effect.IO._
import java.io.{FileInputStream, File}
import java.nio.channels.ByteChannel
import scalaz.{Free, Show, Order, Monoid}

/**
 * A minimalistic verison of a lazy ByteString.
 */
sealed trait LByteString {
  import lbyteString._
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

  def isEmpty: Boolean = this match {
    case Empty() => true
    case _ => false
  }

  /**
   * The 'cons' operation, pre-pending the given byte to this ByteString. This operation
   * creates a singleton byte array with the given byte as first element, and this bytestring instance as the rest of the chunks.
   */
  def #::(b: => Byte): LByteString = Chunk(byteString.singleton(b), this)

  def head: Byte = this match {
    case Empty() => sys.error("head on empty LByteString")
    case Chunk(c, cs) => c.head
  }

  def tail: LByteString = this match {
    case Empty() => sys.error("tail on empty LByteString")
    case Chunk(c, cs) => if (c.length == 1) cs
                         else Chunk(c.tail, cs)
  }

  /**
   * A stricter version of cons, evaluating the first chunk to see whether it is more efficient to
   * coalesce the byte into this chunk rather than to create a new chunk by default.
   * @param b
   * @return
   */
  def conss(b: Byte): LByteString = fold(empty = singleton(b), chunk = (sb, lb) =>
    if (sb.length < 16) Chunk(b &: sb, lb) else Chunk(byteString.singleton(b), lb)
  )

  def append(ys: => LByteString): LByteString = fold(empty = ys, chunk = (sb, lb) =>
    Chunk(sb, lb append ys)
  )

  def uncons: Option[(Byte, LByteString)] = this match {
    case Empty() => None
    case Chunk(c, cs) => Some(c.head, if (c.length == 1) cs else Chunk(c.tail, cs))
  }

  def takeWhile(p: Byte => Boolean): LByteString = this match {
    case Empty() => Empty.apply
    case Chunk(c, cs) => {
      val bs = c.takeWhile(p)
      if (bs.isEmpty) Empty.apply
      else if (bs.length == c.length) Chunk(c, cs.takeWhile(p))
      else Chunk(bs, Empty.apply)
    }
  }
//-- | 'takeWhile', applied to a predicate @p@ and a ByteString @xs@,
//-- returns the longest prefix (possibly empty) of @xs@ of elements that
//-- satisfy @p@.
//takeWhile :: (Word8 -> Bool) -> ByteString -> ByteString
//takeWhile f cs0 = takeWhile' cs0
//  where takeWhile' Empty        = Empty
//        takeWhile' (Chunk c cs) =
//          case findIndexOrEnd (not . f) c of
//            0                  -> Empty
//            n | n < S.length c -> Chunk (S.take n c) Empty
//              | otherwise      -> Chunk c (takeWhile' cs)
}

object LByteString {

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

  def singleton(b: => Byte): LByteString = Chunk(byteString.singleton(b), Empty.apply)

  def readFile(f: File, chunkSize: Int = byteString.DefaultChunkSize): IO[LByteString] =
      IO(new FileInputStream(f).getChannel) flatMap(getContents(_, chunkSize))

  def getContents(chan: ByteChannel, capacity: Int = byteString.DefaultChunkSize): IO[LByteString] = {
    def loop: IO[LByteString] = {
      byteString.getContents(chan, capacity).flatMap((bs: ByteString) =>
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
    case x #:: xs => Chunk(x, fromChunks(xs))
  }

  def pack(s: => Stream[Byte]): LByteString = s match {
    case Stream.Empty => Empty.apply
    case x #:: xs => pack(xs) conss x
  }
}

trait LByteStringInstances {
  implicit val lbyteStringInstance: Monoid[LByteString] with Order[LByteString] with Show[LByteString] = new Monoid[LByteString] with Order[LByteString] with Show[LByteString]  {
    def show(f: LByteString) = f.toString.toList

      def append(f1: LByteString, f2: => LByteString) = f1 append f2

      def zero: LByteString = lbyteString.empty

      def order(xs: LByteString, ys: LByteString): scalaz.Ordering = (xs, ys) match {
        case (Empty(), Empty()) => scalaz.Ordering.EQ
        case (Empty(), Chunk(_, _)) => scalaz.Ordering.LT
        case (Chunk(_, _), Empty()) => scalaz.Ordering.GT
        case (Chunk(x, xs), Chunk(y, ys)) => {
          val so = byteString.byteStringInstance.order(x, y)
          if (so == scalaz.Ordering.EQ) order(xs, ys)
          else so
        }
      }

      override def equalIsNatural: Boolean = true
  }
}

object lbyteString extends LByteStringFunctions with LByteStringInstances
