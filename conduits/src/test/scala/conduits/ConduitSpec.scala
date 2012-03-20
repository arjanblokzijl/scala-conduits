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

  "takes the given number of elements" ! check {
    (stream: Stream[Int], n: Int) =>
      (sourceList[Id, Int](stream) >>== take(n)) mustEqual (stream.take(n))
  }

  "consume all elements" ! check {
    (stream: Stream[Int]) =>
      (sourceList[Id, Int](stream) >>== consume) mustEqual (stream)
  }

  "filter" ! check {(stream: Stream[Int]) =>
    (sourceList[Id, Int](stream) >>== (filter[Id, Int](i => i % 2 == 0) =% consume)) mustEqual(stream.filter(i => i % 2 == 0))
  }

  "head takes the first element, if available" ! check {
    (stream: Stream[Int]) =>
      (sourceList[Id, Int](stream) >>== head) mustEqual (stream.headOption)
  }

  "peek returns the next element, if available" ! check {
    (stream: Stream[Int]) =>
      (sourceList[Id, Int](stream) >>== head) mustEqual (stream.headOption)
  }

  "conduits" should {
    "head removes the first element from the inputstream" in {
      val s = Stream.from(0).take(5)
      val headAndConsume = for (a <- head[Id, Int];
                                b <- consume[Id, Int]) yield (a, b)
      (sourceList[Id, Int](s) >>== headAndConsume) mustEqual((Some(0), Stream.from(1).take(4)))
    }
    "peek does not alter the inputstream" in {
      val s = Stream.from(0).take(5)
      val peekAndConsume = for (a <- peek[Id, Int];
                                b <- consume[Id, Int]) yield (a, b)
      (sourceList[Id, Int](s) >>== peekAndConsume) mustEqual((Some(0), Stream.from(0).take(5)))
    }
  }
}

