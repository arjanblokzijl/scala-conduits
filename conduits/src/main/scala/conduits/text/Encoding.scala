package conduits
package text

import binary.ByteString
import java.nio.charset.{CharsetDecoder, Charset, CharsetEncoder}

/**
 * User: arjan
 */

object Encoding {

  def encode(t: Text, encoder: CharsetEncoder): ByteString =
     ByteString.fromByteBuffer(encoder.encode(t.toCharBuffer))

  def encodeUtf8(t: Text): ByteString =
    ByteString.fromByteBuffer(Charset.forName("UTF-8").newEncoder.encode(t.toCharBuffer))

  def decodeUtf8(bs: ByteString): Text =
    decode(bs, Charset.forName("UTF-8").newDecoder)

  def decode(bs: ByteString, decoder: CharsetDecoder): Text =
    Text.fromCharBuffer(decoder.decode(bs.toByteBuffer))

}
