package conduits
package binary

import LByteString._
import scalaz.Free._
import scalaz.std.function._
import scalaz.{Show, Order, Monoid}

sealed trait LByteString {

  import lbyteString._
  def fold[Z](empty: => Z, chunk: (=> SByteString, => LByteString) => Z): Z

  def foldrChunks[A](z: => A)(f: (SByteString, => A) => A): A = {
    def go(bs: => LByteString, z: => A, f: (SByteString, => A) => A): Trampoline[A] = bs match {
      case Empty() => return_(z)
      case Chunk(c, cs) => go(cs, z, f) map (x => f(c, x))
    }
    go(this, z, f).run
  }

  def cons(b: Byte): LByteString = Chunk(byteString.singleton(b), this)

  def conss(b: Byte): LByteString = fold(empty = singleton(b), chunk = (sb, lb) =>
    if (sb.length < 16) Chunk(sb cons b, lb) else Chunk(byteString.singleton(b), lb)
  )

  def append(ys: => LByteString): LByteString = fold(empty = ys, chunk = (sb, lb) =>
    Chunk(sb, lb append ys)
  )
}

object LByteString {

  object Empty {
    def apply = new LByteString {
      def fold[Z](empty: => Z, chunk: (=> SByteString, => LByteString) => Z): Z = empty
    }
    def unapply(bs: LByteString): Boolean = bs.fold(true, (_, _) => false)
  }

  object Chunk {
    def apply(bs: => SByteString, lbs: => LByteString) = new LByteString {
      def fold[Z](empty: => Z, chunk: (=> SByteString, => LByteString) => Z): Z = chunk(bs, lbs)
    }
    def unapply(bs: LByteString): Option[(SByteString, LByteString)] = bs.fold(None, (b, lb) => Some(b, lb))
  }
}

trait LByteStringFunctions {
  def empty: LByteString = Empty.apply

  def singleton(b: => Byte): LByteString = Chunk(byteString.singleton(b), Empty.apply)

  def fromChunks(s: => Stream[SByteString]): LByteString = s match {
    case Stream.Empty => Empty.apply
    case x #:: xs => Chunk(x, fromChunks(xs))
  }

  def fromByteStream(s: => Stream[Byte]): LByteString = s match {
    case Stream.Empty => Empty.apply
    case x #:: xs => fromByteStream(xs) conss x
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
          if (so == scalaz.Ordering.EQ) so
          else order(xs, ys)
        }
      }

      override def equalIsNatural: Boolean = true
  }
}

object lbyteString extends LByteStringFunctions
