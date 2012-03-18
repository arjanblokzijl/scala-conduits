package conduits

import org.specs2.mutable.Specification
import scalaz._
import std.anyVal._
import effect.IO
import effect.IO._
import scalaz.Id

import CL._

/**
* User: arjan
*/
class ConduitSpec extends Specification {
  val source = CL.sourceList[Id, Int]((1 to 20).toStream)
  val sinkTake = CL.take[Id, Int](10)

  "take" should {
    "consume the given number of elements" in {
      (source >>== sinkTake) mustEqual(Stream.from(1).take(10))
    }
  }
  "consume" should {
    "consume all elements in the source" in {
      (source >>== consume) mustEqual(Stream.from(1).take(20))
    }
  }
}

