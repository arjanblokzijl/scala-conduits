package conduits
package examples

import scalaz.effect.IO
import resourcet._
import resource._

object RunConduits extends App {

  val sink = CL.sumSink[IO]
  val sinkT = CL.sumSink[RTIO]
  val source = CL.sourceList[IO, Int]((1 to 10).toStream)
  val sourceT = CL.sourceList[RTIO, Int]((1 to 10).toStream)

  val rt: IO[Int] = source >>== sink
  val rt2 = sourceT >>== sinkT


  println("result simple " + rt.unsafePerformIO)
  println("result resourceT " + runResourceT(rt2).unsafePerformIO)
}
