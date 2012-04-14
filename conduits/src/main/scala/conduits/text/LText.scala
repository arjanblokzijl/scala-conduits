package conduits
package text


//TODO reduce duplication found in here and LText without going through too much type annotation hassle.
import LText._
import scalaz.{Show, Order, Monoid}
import scalaz.effect.IO
import scalaz.effect.IO._
import java.io.{FileInputStream, File}
import java.nio.channels.ByteChannel

/**
 * A minimal, lazy version of Text
 */
sealed trait LText  {
  def fold[Z](empty: => Z, chunk: (=> Text, => LText) => Z): Z

  def isEmpty: Boolean = this match {
    case Empty() => true
    case _ => false
  }

  /**
   * The 'cons' operation, pre-pending the given byte to this ByteString. This operation
   * creates a singleton byte array with the given byte as first element, and this bytestring instance as the rest of the chunks.
   */
  def #::(c: => Char): LText = Chunk(Text.singleton(c), this)

  /**
   * Takes the given number of bytes from this bytestring.
   */
  def take(n: Int): LText = this match {
    case Empty() => Empty.apply
    case Chunk(c, css) =>
      if (n <= 0) Empty.apply
      else {
        val cs = c.take(n)
        if (cs.length < c.length) Chunk(cs, Empty.apply)
        else Chunk(cs, css take (n - cs.length))
      }
  }

  def append(ys: => LText): LText = fold(empty = ys, chunk = (t, lt) =>
    Chunk(t, lt append ys)
  )

  def uncons: Option[(Char, LText)] = this match {
    case Empty() => None
    case Chunk(c, cs) => Some(c.head, if (c.length == 1) cs else Chunk(c.tail, cs))
  }

  def takeWhile(p: Char => Boolean): LText = this match {
    case Empty() => Empty.apply
    case Chunk(c, cs) => {
      val bs = c takeWhile p
      if (bs.isEmpty) Empty.apply
      else if (bs.length == c.length) Chunk(c, cs takeWhile p)
      else Chunk(bs, Empty.apply)
    }
  }

  def dropWhile(p: Char => Boolean): LText = this match {
    case Empty() => Empty.apply
    case Chunk(c, cs) => {
      val bs = c dropWhile p
      if (bs.isEmpty) cs.dropWhile(p)
      else Chunk(bs, cs)
    }
  }
  /**
   * Gets the head of this byteString, if it is not empty.
   */
  def head: Option[Char] = this match {
    case Empty() => None
    case Chunk(c, cs) => Some(c.head)
  }

  /**
   * Gets the tail of this byteString, if it is not empty.
   */
  def tailOption: Option[LText] = this match {
    case Empty() => None
    case Chunk(c, cs) => if (c.length == 1 && !cs.isEmpty) Some(cs)
                         else if (c.length == 1 && c.isEmpty) None
                         else if (c.isEmpty && !cs.isEmpty) Some(cs)
                         else Some(Chunk(c.tail, cs))
  }

  def unpack: Stream[Char] = this match {
    case Empty() => Stream.Empty
    case Chunk(c, cs) => c.toStream #::: cs.unpack
  }

  def map(f: Char => Char): LText = this match {
    case Empty() => Empty.apply
    case Chunk(c, cs) => Chunk(Text.fromSeq(c map f), cs map f)
  }
}


object LText extends LTextFunctions with LTextInstances {

  object Empty {
    def apply = new LText {

      def fold[Z](empty: => Z, chunk: (Text, Chunked[Char, Text]) => Z) = null

      def fold[Z](empty: => Z, chunk: (=> Text, => LText) => Z): Z = empty
    }
    def unapply(lt: LText): Boolean = lt.fold(true, (_, _) => false)
  }

  object Chunk {
    def apply(bs: => Text, lbs: => LText) = new LText {
      def fold[Z](empty: => Z, chunk: (=> Text, => LText) => Z): Z = chunk(bs, lbs)
    }
    def unapply(lt: LText): Option[(Text, LText)] = lt.fold(None, (c, cs) => Some(c, cs))
  }
}

sealed trait LTextFunctions {
  def empty: LText = Empty.apply

  val DefaultChunkSize: Int = 8*1024

  def readFile(f: File, chunkSize: Int = DefaultChunkSize): IO[LText] =
      IO(new FileInputStream(f).getChannel) flatMap(getContents(_, chunkSize))

  def getContents(chan: ByteChannel, capacity: Int = DefaultChunkSize): IO[LText] = {
    def loop: IO[LText] = {
      Text.getContents(chan, capacity).flatMap((bs: Text) =>
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


  def fromChunks(s: => Stream[Text]): LText = s match {
    case Stream.Empty => Empty.apply
    case x #:: xs => if (x.isEmpty) fromChunks(xs)
                     else Chunk(x, fromChunks(xs))
  }

  def pack(s: => Stream[Char], chunkSize: Int = Text.DefaultChunkSize): LText = s match {
      case Stream.Empty => Empty.apply
      case _ => {
        val (xs, xss) = s.splitAt(chunkSize)
        val head = new Text(xs.toArray)
        if (xss isEmpty) Chunk(head, Empty.apply)
        else Chunk(head, pack(xss))
      }
    }
}

sealed trait LTextInstances {
  implicit val lTextStringInstance: Monoid[LText] with Order[LText] with Show[LText] = new Monoid[LText] with Order[LText] with Show[LText]  {
    def show(f: LText) = f match {
      case Empty() => "<Empty>".toList
      case Chunk(c, cs) => Text.textInstance.show(c) ::: show(cs)
    }

    def append(f1: LText, f2: => LText) = f1 append f2

    def zero: LText = LText.empty

    def order(xs: LText, ys: LText): scalaz.Ordering = (xs, ys) match {
      case (Empty(), Empty()) => scalaz.Ordering.EQ
      case (Empty(), Chunk(_, _)) => scalaz.Ordering.LT
      case (Chunk(_, _), Empty()) => scalaz.Ordering.GT
      case (Chunk(x, xs), Chunk(y, ys)) => {
        val so = Text.textInstance.order(x, y)
        if (so == scalaz.Ordering.EQ) order(xs, ys)
        else so
      }
    }

    override def equalIsNatural: Boolean = true
  }
}