package conduits
package benchmark

import empty.Void
import scalaz._
import Id._
import CL._
import binary._
import bs._
import Conduits._

object BinaryBenchmark extends MyRunner { val cls = classOf[BinaryBenchmark]}

class BinaryBenchmark extends CBenchmark with BenchmarkData {

  def binaryLines(data: Array[Byte]): DList[ByteString] = {
    var total = DList[ByteString]()
    total = sourceList[Id, ByteString](Stream(ByteString(data))) %%== Binary.lines[Id] =% consumeDlist
    total
  }

  def timeLines(reps:Int) = run(reps)(binaryLines(byteArr))
}

