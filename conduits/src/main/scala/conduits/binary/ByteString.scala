package conduits
package binary

import java.nio.ByteBuffer
import java.io.{FileInputStream, File}
import java.nio.channels.ByteChannel
import scalaz.effect.IO
import IO._
import scala.io.Codec

/**
 * User: arjan
 */

sealed trait IOMode
class Read extends IOMode
class Write extends IOMode

//TODO create lazy, chunked version of this.
sealed trait ByteString {
  /**Returns a read-only Buffer that is wrapped by this ByteString*/
  def asByteBuffer: java.nio.ByteBuffer

  def isEmpty = this match {
    case Empty => true
    case _ => false
  }
}

class ArrayByteString(private val bytes: Array[Byte]) extends ByteString {
  def asByteBuffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer
}

object Empty extends ByteString {
  def asByteBuffer = ByteBuffer.wrap(Array.empty[Byte])
}

trait ByteStringFunctions {
  val DefaultBufferSize = 8*1024
  /** Converts a `java.nio.ByteBuffer` into a `ByteString`. */
  def fromByteBuffer(bytes: java.nio.ByteBuffer): ByteString = {
    bytes.rewind()
    val ar = new Array[Byte](bytes.remaining)
    bytes.get(ar)
    new ArrayByteString(ar)
  }

  def readFile(f: File): IO[ByteString] =
    IO(new FileInputStream(f).getChannel).flatMap(c => getContents(c))

  def getContents(chan: ByteChannel, capacity: Int = DefaultBufferSize): IO[ByteString] = {
    val buf = java.nio.ByteBuffer.allocate(capacity)
    IO(chan.read(buf)).map(i => i match {
      case -1 => Empty
      case _ => fromByteBuffer(buf)
    })
  }
}

trait ByteStringInstances {
  //todo show, equal, etc..
}

object byteString extends ByteStringInstances with ByteStringFunctions {

}

