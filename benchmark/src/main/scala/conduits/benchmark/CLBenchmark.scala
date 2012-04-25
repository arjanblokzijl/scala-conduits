package conduits
package benchmark

import pipes._
import ConduitFunctions._
import CL._
import Conduits._
import scalaz._

object CLBenchmark extends MyRunner { val cls = classOf[CLBenchmark] }

class CLBenchmark extends CBenchmark with BenchmarkData {

  def foldListDirect(data:Stream[Int]):Int = {
    var total = 0
    total = data.foldLeft(0)(_+_)
    total
  }

  def conduitFoldList(data:Stream[Int]):Int = {
    var total = 0
    total = CL.sourceList[Id, Int](data) %%== CL.sum[Id]
    total
  }

  def timeFoldStreamDirect(reps:Int) = run(reps)(foldListDirect(intStream))
  def timeConduitFoldStream(reps:Int) = run(reps)(foldListDirect(intStream))
}
