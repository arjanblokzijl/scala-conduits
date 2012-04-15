package conduits
package text

import binary.ByteString
import pipes._
import Pipe._
import resourcet.MonadThrow
import scalaz.Validation
import scalaz.effect.IO
import java.nio.charset.UnmappableCharacterException


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
    def push(bs: ByteString): Conduit[ByteString, F, Text] =
      HaveOutput(NeedInput(push, close(bs)), close2(bs), codec.codecDecode(bs))

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

}

/**
 * A specific character encoding.
 */
sealed trait Codec {
  def codecName: Text
  def codecEncode(t: Text): (ByteString, Option[(TextException, Text)])
  def codecDecode(b: ByteString): Text
}

class Utf8 extends Codec {
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

  def codecDecode(bs: ByteString) =
    Encoding.decodeUtf8(bs)
}

sealed trait TextException extends Exception

case class DecodeException(codec: Codec, b: Byte) extends TextException

case class EncodeException(codec: Codec, c: Char) extends TextException



