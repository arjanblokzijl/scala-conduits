package conduits
package text

import binary.ByteString
import java.nio.charset.{Charset, CharsetDecoder, CharsetEncoder}

/**
 * User: arjan
 */

object Encoding {

  def charset(name: String): Charset = Charset.forName(name)

  val UTF8 = charset("UTF-8")
  val UTF16 = charset("UTF-16")
  val ISO8859 = charset("ISO-8859-1")
  val UTF16BE = charset("UTF-16BE")
  val UTF16LE = charset("UTF-16LE")
  val ASCII = charset("ASCII")

  def encode(t: Text, encoder: CharsetEncoder): ByteString = {
    val bs = encoder.encode(t.toCharBuffer)
    ByteString.fromByteBuffer(bs, bs.limit)
  }

  def encodeUtf8(t: Text): ByteString =
    encode(t, UTF8.newEncoder)

  def encodeUtf16Le(t: Text): ByteString =
    encode(t, UTF16LE.newEncoder)

  def decodeUtf8(bs: ByteString): Text =
    decode(bs, UTF8.newDecoder)

  def decodeUtf16Le(bs: ByteString): Text =
    decode(bs, UTF16LE.newDecoder)

  def decode(bs: ByteString, decoder: CharsetDecoder): Text =
    Text.fromCharBuffer(decoder.decode(bs.toByteBuffer))
}
