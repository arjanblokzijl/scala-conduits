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
    total = total.append(ByteString(data))
    total
  }

  def fingerTreeByteStringAppend(data: Array[Byte]): FingerTreeByteString = {
    var total = FingerTreeByteString.empty
    total = total ++ FingerTreeByteString(data)
    total
  }

  def timeByteString(reps:Int) = run(reps)(byteStringAppend(byteArr))
  def timeFingerTree(reps:Int) = run(reps)(fingerTreeByteStringAppend(byteArr))
}