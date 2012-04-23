package conduits
package text

import pipes._
import Pipe._
import resourcet.MonadThrow
import scalaz.effect.IO
import scalaz._
import std.anyVal.char
import std.anyVal.byteInstance
import LazyOption._
import std.function._
import ConduitFunctions._
import binary.{Char8, ByteString}
import java.nio.charset.{CharsetDecoder, UnmappableCharacterException}


object CText {

  /**
   * Convert text into bytes, using the provided codec. If the codec is
   * unable to represent an input character, an exception is thrown.
   */
  def encode[F[_]](codec: Codec)(implicit MT: MonadThrow[F]): Conduit[Text, F, ByteString] = {
    implicit val M = MT.M
    CL.mapM[F, Text, ByteString](t => {
      val (bx, mexc) = codec.codecEncode(t)
      mexc.map(res => MT.monadThrow[ByteString](res._1)).getOrElse(M.point(bx))
    })
  }

  /**
   * Convert bytes into text, using the provided codec. If the codec is
   * not capable of decoding an input byte sequence, an Exception will be thrown.
   */
  def decode[F[_]](codec: Codec)(implicit MT: MonadThrow[F]): Conduit[ByteString, F, Text] = {
    implicit val M = MT.M

    def push(bs: ByteString): Conduit[ByteString, F, Text] = {
      val (text, extra) = codec.codecDecode(bs)
      extra match {
        case Left((exc, _)) => PipeM(MT.monadThrow(exc), MT.monadThrow(exc))
        case Right(bs1) => {
          def app(bs2: ByteString): ByteString = bs1 append(bs2)
          def close1 = close(bs1)
          if (bs1.isEmpty) HaveOutput(NeedInput(push, close1), close2(bs), text)
          else HaveOutput(NeedInput(bs => push(app(bs)), close1), close2(bs), text)
        }
      }
    }

    def close(bs: ByteString): Conduit[ByteString, F, Text] = bs.uncons match {
      case None => Done(None, ())
      case Some((w, _)) => {
        val exc: F[pipes.Conduit[ByteString, F, Text]] = MT.monadThrow[Conduit[ByteString, F, Text]](DecodeException(codec, w))
        PipeM(exc, M.map(exc)(_ => ()))
      }
    }
    def close2(bs: ByteString): F[Unit] = bs.uncons match {
      case None => M.point(())
      case Some((w, _)) => MT.monadThrow[Unit](DecodeException(codec, w))
    }

    NeedInput(push, close(ByteString.empty))
  }

  /**
   * Split the given Text into lines.
   */
  def lines[F[_]](implicit M: Monad[F]): Conduit[Text, F, Text] = {
    import scalaz.std.stream._
    def add[A](l1: Stream[A], l2: => Stream[A]): Stream[A] = streamInstance.plus(l1, l2)
    def push[S](front: => Stream[Text], bs: => Text): F[ConduitStateResult[Stream[Text], Text, Text]] = {
      val (leftover, ls) = getLines(front, bs)
      M.point(StateProducing(leftover, ls))
    }

    def close(front: => Stream[Text]): F[Stream[Text]] =
      if (front.isEmpty) M.point(Stream.Empty)
      else M.point(front)


    def getLines(front: Stream[Text], bs: => Text): (Stream[Text], Stream[Text]) = {
      val (x, y) = bs.span(_ != '\n')
      if (bs.isEmpty) (Stream.Empty, front)
      else if (y.isEmpty) (Stream(x), front)
      else getLines(add(front, Stream(x)), y.drop(1))
    }
    conduitState(Stream.Empty, push, close)
  }

  /**
   * Evaluates the first argument, and returns LazySome if
   * no exception occurs. Otherwise, LazyNone is returned.
   */
  private[text] def maybeDecode[A, B](a: => A, b: => B): LazyOption[(A, B)] =
     try {
       val a1 = a//evaluate first argument
       lazySome(a1, b)
     } catch {case e: Throwable => lazyNone}

  private[text] def byteSplits(bytes: ByteString): Seq[(ByteString, ByteString)] = {
    def loop(n: Int): Seq[(ByteString, ByteString)] =
      if (n == 0) Seq((ByteString.empty, bytes))
      else bytes.splitAt(n) +: loop(n - 1)
    loop(bytes.length)
  }

  private[text] def splitSlowly(dec: ByteString => Text, bytes: ByteString): (Text, Either[(TextException, ByteString), ByteString]) = {
    val splits = byteSplits(bytes)
    def tryDec(bs: ByteString, dec: ByteString => Text): Either[TextException, Text] =
       try {
         Right(dec(bs))
       } catch {case e: TextException => Left(e)}

    def decFirst(bs1: ByteString, bs2: ByteString): Option[(Text, Either[(TextException, ByteString), ByteString])] = {
      tryDec(bs1, dec) match {
        case Left(_) => None
        case Right(text) => Some((text, tryDec(bs2, dec) match {
          case Left(exc) => Left(exc, bs2)
          //we can't get to the match below, since this method is only called as fallback
          //after normal parsing has failed to provide better error reporting.
          case Right(_) => Right(ByteString.empty)
        }))
      }
    }
    splits.flatMap{case (a, b) => decFirst(a, b)}.head
  }
}

/**
 * A specific character encoding.
 */
sealed trait Codec {
  def codecName: Text
  def codecEncode(t: Text): (ByteString, LazyOption[(TextException, Text)])
  def codecDecode(bs: ByteString): (Text, Either[(TextException, ByteString), ByteString])
  def defaultDecode(bs: ByteString)(decoder: CharsetDecoder): (Text, Either[(TextException, ByteString), ByteString]) =
    try {
      (Encoding.decodeUtf8(bs), Right(ByteString.empty))
    } catch {
      case e: UnmappableCharacterException => {
        val index = e.getInputLength
        val byte = bs(index)
        (Text.empty, Left((DecodeException(this, byte)), bs.splitAt(index)._2))
      }
    }
}

object Utf8 extends Codec {
  def codecName = Text.pack("UTF-8")

  def codecEncode(t: Text) = {
    try {
      (Encoding.encodeUtf8(t), lazyNone)
    } catch {
      case e: UnmappableCharacterException => {
        val index = e.getInputLength
        val char = t(index)
        (ByteString.empty, lazySome((EncodeException(this, char)), Text.empty))
      }
    }
  }

  def codecDecode(bs: ByteString) = {
    val maxN = bs.length

    def required(b: Byte): Int =
      if ((b & 0x80) == 0x00) 1
      else if ((b & 0xE0) == 0xC0) 2
      else if ((b & 0xF0) == 0xE0) 3
      else if ((b & 0xF8) == 0xF0) 4
      else 0

    def loop(n: Int): LazyOption[(Text, ByteString)] = {
      if (n == maxN) lazySome(Encoding.decodeUtf8(bs), ByteString.empty)
      else {
        val req = required(bs(n))
        def tooLong = {
          val (bs1, bs2) = bs.splitAt(n)
          lazySome(Encoding.decodeUtf8(bs1), bs2)
        }
        def decodeMore: LazyOption[(Text, ByteString)] = {
          if (req == 0) lazyNone
          else if (n + req > maxN) tooLong
          else loop(n + req)
        }
        decodeMore
      }
    }

    def splitQuickly(bs: ByteString): LazyOption[(Text, ByteString)] = loop(0).flatMap(tb => CText.maybeDecode(tb._1, tb._2))

    splitQuickly(bs).fold(te => (te._1, Right(te._2)), CText.splitSlowly(Encoding.decodeUtf8, bs))
  }
}

object Ascii extends Codec {

  def codecName = Text.pack("ASCII")

  def codecEncode(t: Text) = {
    val (safe, unsafe) = t.span(c => char.lessThanOrEqual(c, 0x7F.toChar))
    val bytes = Char8.pack(safe.unpack)
    val extra: LazyOption[(TextException, Text)] = if (unsafe.isEmpty) lazyNone
                else lazySome((EncodeException(Ascii, unsafe.head), unsafe))
    (bytes, extra)
  }

  def codecDecode(bs: ByteString) = {
    val (safe, unsafe) = bs.span(b => byteInstance.lessThanOrEqual(b, 0x7F.toByte))
    val text = Text.pack(Char8.unpack(safe))
    val extra = if (unsafe.isEmpty) Right(ByteString.empty)
                else Left(DecodeException(Ascii, unsafe.head), unsafe)
    (text, extra)
  }
}

sealed trait TextException extends Exception
case class DecodeException(codec: Codec, b: Byte) extends TextException
case class EncodeException(codec: Codec, c: Char) extends TextException



