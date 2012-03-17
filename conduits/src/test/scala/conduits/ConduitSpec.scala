//package conduits
//
//import org.specs2.mutable.Specification
//import scalaz._
//import std.anyVal._
//import effect.IO
//import effect.IO._
//import scalaz.Id
//
//
///**
// * User: arjan
// */
//
//class ConduitSpec extends Specification {
//  val source = CL.sourceList[Id, Int]((1 to 20).toStream)
//  val sinkTakeM = CL.takeM[Id, Int](10)
//
//  "takeM" should {
//    "consume the given number of elements" in {
//      (source >>== sinkTakeM) mustEqual(Stream.from(1).take(10))
//    }
//  }
//}


//TODO specs2 doesn't work with Scala 2.10?