package conduits
package examples

import scalaz.effect.IO
import scalaz.Id
import resourcet._
import resource._

object RunConduits extends App {

  val sinkSum = CL.sumSink[IO]
  val sinkTake = CL.take[Id, Int](10)
  val sinkT = CL.sumSink[RTIO]
  val source = CL.sourceList[IO, Int]((1 to 10).toStream)
  val sourceId = CL.sourceList[Id, Int]((1 to 15).toStream)
//  val sourceLarge = CL.sourceList[Id, Int](Stream.from(1))
  val sourceT = CL.sourceList[RTIO, Int]((1 to 10).toStream)

  val mapSource = sourceId %= CL.map[Id, Int, Int](i => i + 1)// =% sinkTake
//  val mapSourceLarge = sourceLarge %= CL.map[Id, Int, Int](i => i + 1)// =% sinkTake
  val rt: IO[Int] = source >>== sinkSum
  val rtMap = sourceId >>== sinkTake
  val rt2 = sourceT >>== sinkT

  println("result io " + rt.unsafePerformIO)
  println("result map " + (mapSource >>== sinkTake).take(15).force)
  println("result resourceT " + runResourceT(rt2).unsafePerformIO)
}
