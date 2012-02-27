package conduits
package examples

import scalaz.effect.IO
import resource._
object RunConduits extends App {
  val sink = CL.sumSink[IO]
  val source = CL.sourceList[IO, Int]((1 to 10).toStream)

  val rt = source >>== sink
  val runRt: IO[Int] = runResourceT(rt)

  println("result " + runRt.unsafePerformIO)
}
