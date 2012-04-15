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

  def encode(t: Text, encoder: CharsetEncoder): ByteString = {
    val bs = encoder.encode(t.toCharBuffer)
    ByteString.fromByteBuffer(bs, bs.limit)
  }

  def encodeUtf8(t: Text): ByteString = {
    val encoder: CharsetEncoder = Charset.forName("UTF-8").newEncoder
    val bb = encoder.encode(t.toCharBuffer)
    ByteString.fromByteBuffer(encoder.encode(t.toCharBuffer), bb.limit)
  }

  def decodeUtf8(bs: ByteString): Text =
    decode(bs, UTF8.newDecoder)

  def decode(bs: ByteString, decoder: CharsetDecoder): Text =
    Text.fromCharBuffer(decoder.decode(bs.toByteBuffer))
}
