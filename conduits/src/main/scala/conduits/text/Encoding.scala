package conduits
package text

import binary.ByteString
import java.nio.charset.{Charset, CharsetDecoder, CharsetEncoder}

/**
 * User: arjan
 */

object Encoding {

  val UTF8 = Charset.forName("UTF-8")

  def encode(t: Text, encoder: CharsetEncoder): ByteString =
     ByteString.fromByteBuffer(encoder.encode(t.toCharBuffer))

  def encodeUtf8(t: Text): ByteString = {
    val encoder: CharsetEncoder = Charset.forName("UTF-8").newEncoder
    val bb = encoder.encode(t.toCharBuffer)
    ByteString.fromByteBuffer(encoder.encode(t.toCharBuffer), bb.remaining)
  }

  def decodeUtf8(bs: ByteString): Text =
    decode(bs, UTF8.newDecoder)

  def decode(bs: ByteString, decoder: CharsetDecoder): Text =
    Text.fromCharBuffer(decoder.decode(bs.toByteBuffer))
}
