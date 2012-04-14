package conduits
package text

import binary.ByteString
import pipes._
import Pipe._
import resourcet.MonadThrow


object CText {
  def decode[F[_]](codec: Codec)(implicit MT: MonadThrow[F]): Conduit[ByteString, F, Text] = {
    implicit val M = MT.M
    def push(bs: ByteString): Conduit[ByteString, F, Text] =
      HaveOutput(NeedInput(push, close(bs)), close2(bs), codec.codecDecode(bs))

    def close(bs: ByteString): Conduit[ByteString, F, Text] = bs.uncons match {
      case None => Done(None, ())
      case Some((w, _)) => {
        val exc = MT.monadThrow[Conduit[ByteString, F, Text]](DecodeException(codec, w))
        PipeM(exc, M.point(()))
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

  def codecEncode(t: Text) = (Encoding.encodeUtf8(t), None)

  def codecDecode(bs: ByteString) =
    Encoding.decodeUtf8(bs)
}

sealed trait TextException extends Exception
case class DecodeException(codec: Codec, b: Byte) extends TextException
case class EncodeException(codec: Codec, c: Char) extends TextException
