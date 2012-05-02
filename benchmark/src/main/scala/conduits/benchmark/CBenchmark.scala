package conduits
package benchmark

import com.google.caliper.Runner
import com.google.caliper.SimpleBenchmark
import com.google.caliper.Param
import scala.util.Random
import Random._
import pipes._
import ConduitFunctions._
import CL._
import Conduits._
import scalaz._
import DList._

trait CBenchmark extends SimpleBenchmark {
  /**
   * Sugar for building arrays using a per-cell init function.
   */
  def init[A:Manifest](size:Int)(init: => A) = {
    val data = Array.ofDim[A](size)
    for (i <- 0 until size) data(i) = init
    data
  }

  /**
   * Sugar to run 'f' for 'reps' number of times.
   */
  def run(reps:Int)(f: => Unit) = for(i <- 0 until reps)(f)

}

/**
 * Extend this to create a main object which will run 'cls' (a benchmark).
 */
trait MyRunner {
  val cls:java.lang.Class[_ <: com.google.caliper.Benchmark]
  def main(args:Array[String]): Unit = Runner.main(cls, args:_*)
}

trait BenchmarkData extends CBenchmark {
  //val size = 10 * 1000
  //val size = 100 * 1000
  //val size = 200 * 1000
  //val size = 1 * 1000 * 1000
  //val size = 4 * 1000 * 1000
  val size = 100 * 1000

  lazy val intArr = init(size)(nextInt)
  lazy val byteArr: Array[Byte] = init[Byte](size)(nextInt.toByte)
  lazy val intSeq = (10 to size).toList
  lazy val intStream = Stream.from(0).take(size)
  lazy val intSource = CL.sourceList[Id, Int](Stream.from(0).take(size))
}