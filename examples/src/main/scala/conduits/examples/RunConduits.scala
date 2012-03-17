package conduits
package examples

import scalaz._
import std.anyVal._
import std.stream._
import effect.IO
import effect.IO._
import scalaz.Id
import resourcet._
import resource._

object RunConduits extends App {

  val sinkSum = CL.sumSink[IO]
  val sinkTake = CL.take[IO, Int](10)
  val sinkTakeS = CL.take[Stream, Int](10)
  val sinkConsume = CL.consume[Stream, Int]
  val sinkTakeId = CL.take[Id, Int](10)
  val sinkT = CL.sumSink[RTIO]
  val sourceStream = CL.sourceList[Stream, Int]((1 to 15).toStream)
  val sourceId = CL.sourceList[Id, Int]((1 to 15).toStream)
  val sourceLarge = CL.sourceList[IO, Int](Stream.from(1).take(100000))
  val sourceLargeId = CL.sourceList[Id, Int](Stream.from(1).take(100000))
  val sourceT = CL.sourceList[RTIO, Int]((1 to 10).toStream)

  val mapSource = sourceId %= CL.map[Id, Int, Int](i => i + 1)// =% sinkTake
  val mapSourceLarge = sourceLarge %= CL.map[IO, Int, Int](i => i + 1)// =% sinkTake
  val mapSourceLargeId = sourceLargeId %= CL.map[Id, Int, Int](i => i + 1)// =% sinkTake

  println("result take " + (sourceId >>== sinkTakeId).take(15).force)
  println("result take " + (sourceStream >>== sinkTakeS).flatten.take(15).force)
  println("result consume " + (sourceStream >>== sinkConsume).flatten.take(15).force)
  println("result large map io " + (mapSourceLarge >>== sinkTake).unsafePerformIO.take(15).force)
  println("result large map id " + (mapSourceLargeId >>== sinkTakeId).take(15).force)
  println("result resourceT " + runResourceT(sourceT >>== sinkT).unsafePerformIO)
}
