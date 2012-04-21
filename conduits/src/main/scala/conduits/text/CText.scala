package conduits
package text

import binary.ByteString
import pipes._
import Pipe._
import resourcet.MonadThrow
import scalaz.effect.IO
import java.nio.charset.UnmappableCharacterException
import scalaz._
import LazyOption._
import std.function._


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

  def decode[F[_]](codec: Codec)(implicit MT: MonadThrow[F]): Conduit[ByteString, F, Text] = {
    implicit val M = MT.M

    def push(bs: ByteString): Conduit[ByteString, F, Text] = {
      val (text, extra) = codec.safeCodecDecode(bs)
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
   * Evaluates the first argument, and returns LazySome if
   * no exception occurs. Otherwise, LazyNone is returned.
   */
  def maybeDecode[A, B](a: => A, b: => B): LazyOption[(A, B)] =
     try {
       val a1 = a//evaluate first argument
       lazySome(a1, b)
     } catch {case e: Throwable => lazyNone}
}

/**
 * A specific character encoding.
 */
sealed trait Codec {
  def codecName: Text
  def codecEncode(t: Text): (ByteString, Option[(TextException, Text)])
  def codecDecode(b: ByteString): Text
  def safeCodecDecode(b: ByteString): (Text, Either[(TextException, ByteString), ByteString])
}

object Utf8 extends Codec {
  def codecName = Text.pack("UTF-8")

  def codecEncode(t: Text) = {
    try {
      (Encoding.encodeUtf8(t), None)
    } catch {
      case e: UnmappableCharacterException => {
        val index = e.getInputLength
        val char = t(index)
        (ByteString.empty, Some((EncodeException(this, char)), Text.empty))
      }
    }
  }

  def safeCodecDecode(bs: ByteString) = {
    val maxN = bs.length

    def required(b: Byte): Int =
      if ((b & 0x80) == 0x00) 1
      else if ((b & 0xE0) == 0xC0) 2
      else if ((b & 0xF0) == 0xE0) 3
      else if ((b & 0xF8) == 0xF0) 4
      else 0

    def loop(n: Int): Option[(Text, ByteString)] = {
      if (n == maxN) Some(Encoding.decodeUtf8(bs), ByteString.empty)
      else {
        val req = required(bs(n))
        def tooLong = {
          val (bs1, bs2) = bs.splitAt(n)
          Some(Encoding.decodeUtf8(bs1), bs2)
        }
        def decodeMore: Option[(Text, ByteString)] = {
          if (req == 0) None
          else if (n + req > maxN) tooLong
          else loop(n + req)
        }
        decodeMore
      }
    }

    def splitQuickly(bs: ByteString): Option[(Text, ByteString)] = loop(0).flatMap(tb => CText.maybeDecode(tb._1, tb._2).toOption)

    splitQuickly(bs) match {
      case Some((text, extra)) => (text, Right(extra))
      case None => (codecDecode(bs), Right(ByteString.empty))
    }
  }

  def codecDecode(bs: ByteString) =
    Encoding.decodeUtf8(bs)
}

sealed trait TextException extends Exception

case class DecodeException(codec: Codec, b: Byte) extends TextException

case class EncodeException(codec: Codec, c: Char) extends TextException



