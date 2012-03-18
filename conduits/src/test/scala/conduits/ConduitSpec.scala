package conduits

import org.specs2.mutable.Specification
import scalaz._
import std.anyVal._
import effect.IO
import effect.IO._
import scalaz.Id
import org.scalacheck.Arbitrary._

import CL._
import org.specs2.ScalaCheck

/**
* User: arjan
*/
class ConduitSpec extends Specification with ScalaCheck {

  "takes the given number of elements" ! check {(stream: Stream[Int], n: Int) =>
    (sourceList[Id, Int](stream) >>== take(n)) mustEqual(stream.take(n))
  }

  "consume all elements" ! check {(stream: Stream[Int]) =>
    (sourceList[Id, Int](stream) >>== consume) mustEqual(stream)
  }
}

