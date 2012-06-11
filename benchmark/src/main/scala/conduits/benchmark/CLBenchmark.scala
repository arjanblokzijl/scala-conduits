package conduits
package benchmark

import pipes._
import ConduitFunctions._
import CL._
import Conduits._
import scalaz._
import DList._

object CLBenchmark extends MyRunner { val cls = classOf[CLBenchmark] }

class CLBenchmark extends CBenchmark with BenchmarkData {

   def foldStreamDirect(data:Stream[Int]): Int = {
     var total = 0
     total = data.foldLeft(0)(_+_)
     total
   }

   def takeDListDirect(data:Stream[Int]): List[Int] = {
     var total = List[Int]()
     total = (CL.sourceList[Id, Int](data) %%== CL.takeDList(100000)).toList
     total
   }

   def takeListBuffer(data:Stream[Int]): List[Int] = {
     var total = List[Int]()
     total = (CL.sourceList[Id, Int](data) %%== CL.takeBuffer(100000)).toList
     total
   }

   def takeDListMonoid(data:Stream[Int]): List[Int] = {
     var total = List[Int]()
     total = (CL.sourceList[Id, Int](data) %%== CL.takeId[DList, Int](100000)).toList
     total
   }

   def conduitFoldLeft(data: Source[Id, Int]):Int = {
     var total = 0
     total = data %%== CL.foldLeft(0)(_ + _)
     total
   }

   def timeFoldLeftDirect(reps:Int) = run(reps)(foldStreamDirect(intStream))
   def timeConduitFoldLeft(reps:Int) = run(reps)(conduitFoldLeft(intSource))
   def timeTakeDlistDirect(reps:Int) = run(reps)(takeDListDirect(intStream))
   def timeTakeDlistMonoid(reps:Int) = run(reps)(takeDListMonoid(intStream))
   def timeTakeListBufferMonoid(reps:Int) = run(reps)(takeListBuffer(intStream))
}
