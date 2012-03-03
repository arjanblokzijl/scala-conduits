package conduits
package examples

import scalaz.effect.IO

object RunConduits extends App {
  val sink = CL.sumSink[IO]
  val source = CL.sourceList[IO, Int]((1 to 10).toStream)

  val rt: IO[Int] = source >>== sink

  println("result " + rt.unsafePerformIO)
}
