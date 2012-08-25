package conduits
package benchmark

/**
 * User: arjan
 */

import scalaz._
import Id._
import CL._
import binary._
import bs._
import Conduits._
object ByteStringBenchmark extends MyRunner { val cls = classOf[ByteStringBenchmark]}


class ByteStringBenchmark extends CBenchmark with BenchmarkData {

  def byteStringAppend(data: Array[Byte]): ByteString = {
    var total = ByteString.empty
    total = total ++ ByteString(data)
    total
  }

  def akkaByteStringAppend(data: Array[Byte]): AkkaByteString = {
    var total = AkkaByteString.apply(Array[Byte]())
    total = total ++ AkkaByteString(data)
    total
  }

  def timeByteString(reps:Int) = run(reps)(byteStringAppend(byteArr))
  def timeAkka(reps:Int) = run(reps)(akkaByteStringAppend(byteArr))
}