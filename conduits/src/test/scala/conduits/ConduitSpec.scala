package conduits

import empty.Void
import org.specs2.mutable.Specification
import scalaz._
import std.list._
import std.anyVal._
import effect.IO
import effect.IO._
import scalaz.Id
import org.scalacheck.Arbitrary._
import pipes._
import ConduitFunctions._
import CL._
import org.specs2.ScalaCheck
import Conduits._
import collection.immutable.Stream

/**
 * User: arjan
 */
class ConduitSpec extends Specification with ScalaCheck {

  "takes the given number of elements" ! check {
    (stream: Stream[Int], n: Int) =>
      (sourceList[Id, Int](stream) %%== take(n))  must be_===(stream.take(n))
  }

  "drops the given number of elements" ! check {
    (stream: Stream[Int], n: Int) => {
      val (src2, _) = sourceList[Id, Int](stream) %%==+ drop(n)
      (src2 %%== consume) must be_===(stream.drop(n))
    }
  }

  "consume all elements" ! check {
    (stream: Stream[Int]) =>
      (sourceList[Id, Int](stream) %%== consume)  must be_===(stream)
  }

  "filter" ! check {(stream: Stream[Int]) =>
    (sourceList[Id, Int](stream) %%== (filter[Id, Int](i => i % 2 == 0) =% consume))  must be_===(stream.filter(i => i % 2 == 0))
  }

  "zipping" ! check {(s1: Stream[Int], s2: Stream[Int]) =>
    (sourceList[Id, Int](s1) zip sourceList[Id, Int](s2) %%== consume) must be_===(s1 zip s2)
  }

  "map" ! check {(s1: Stream[Int]) =>
    ((CL.map[Id, Int, Int]((i: Int) => i + 1) %= sourceList(s1)) %%== consume) must be_===(s1 map(_ + 1))
  }

  "concatMap" ! check {(s1: Stream[Int]) =>
    ((CL.concatMap[Id, Int, Int]((i: Int) => (i to (i + 9)).toStream) %= sourceList(s1)) %%== consume) must be_===(s1 flatMap(i => (i to (i + 9)).toStream))
  }

  "enumFromTo" ! check {(i: Int) =>
    val start = math.min(Int.MaxValue - 20, i)
    (CL.enumFromTo[Id, Int](start, start + 9) %%== consume) must be_===((start to (start + 9)).toStream)
  }

  "head takes the first element, if available" ! check {
    (stream: Stream[Int]) =>
      (sourceList[Id, Int](stream) %%== head)  must be_===(stream.headOption)
  }

  "peek returns the next element, if available" ! check {
    (stream: Stream[Int]) =>
      (sourceList[Id, Int](stream) %%== head)  must be_===(stream.headOption)
  }

  "sequence sink" ! check {
    (stream: Stream[Int]) =>
    (sourceList[Id, Int](stream) %= sequence(consume) %%== consume).flatten must be_==(stream)
  }

  "zipping sink take all" ! check {
    (s: Stream[Int]) =>
      (sourceList[Id, Int](s) %%== zipSinks(consume[Id, Int], consume[Id, Int])) must be_==(s, s)
  }

  "conduits" should {
    "head removes the first element from the inputstream" in {
      val s = Stream.from(0).take(5)
      val headAndConsume = for (a <- head[Id, Int];
                                b <- consume[Id, Int]) yield (a, b)
      (sourceList[Id, Int](s) %%== headAndConsume)  must be_===((Some(0), Stream.from(1).take(4)))
    }
    "peek does not alter the inputstream" in {
      val s = Stream.from(0).take(5)
      val peekAndConsume = for (a <- peek[Id, Int];
                                b <- consume[Id, Int]) yield (a, b)
      (sourceList[Id, Int](s) %%== peekAndConsume)  must be_===((Some(0), Stream.from(0).take(5)))
    }

    //TODO groupBy as in Haskell does not have a Scala equivalent, so no useful scalacheck immediately available
    "groupBy" in {
      val s = Stream(1, 1, 1, 2, 2, 2, 2, 3, 3, 4, 5)
      ((CL.groupBy[Id, Int]((a, b) => a == b) %= sourceList(s)) %%== consume) must be_===(Stream(Stream(1, 1, 1), Stream(2, 2, 2, 2), Stream(3, 3), Stream(4), Stream(5)))
    }

    "unfold" in {
      (CL.unfold[Id, Int, Int](i => if (i > 20) None else Some(i, i + 1))(0) %%== CL.consume) must be_==(Stream.from(0).take(21))
    }
  }

  "isolate" should {
    "consume no more than the given number of values" in {
      val s = Stream.from(0)
      val res = (sourceList[Id, Int](s) %= isolate(10) %%== consume)
      res must be_===(s.take(10))
    }
    "consume all data combined with sinkNull" in {
      val s = Stream.from(0).take(20)
      val res = (sourceList[Id, Int](s) %%== (isolate[Id, Int](10) =% sinkNull) flatMap(_ => consume))
      res must be_===(Stream.from(10).take(10))
    }
  }

  "sequence" should {
    "simple sink" in {
      import pipes._
      import ConduitFunctions._
      val s = Stream.from(1).take(11)
      val sumSink: Sink[Int, Id, Int] = head[Id, Int] flatMap(ma => ma match {
        case Some(i) => head[Id, Int].map(mi => i + mi.getOrElse(0))
        case None => pipeMonad[Int, Void, Id].point(0)
      })

      val res = (sourceList[Id, Int](s) %= sequence(sumSink) %%== consume)
      res must be_==(Stream(3, 7, 11, 15, 19, 11))
    }

    "sequence with unpull behaviour" in {
      import pipes._
      import ConduitFunctions._
      val s = Stream.from(1).take(11)
      val sumSink: Sink[Int, Id, Int] = head[Id, Int] flatMap(ma => ma match {
        case Some(i) => peek[Id, Int].map(mi => i + mi.getOrElse(0))
        case None => pipeMonad[Int, Void, Id].point(0)
      })

      val res = (sourceList[Id, Int](s) %= sequence(sumSink) %%== consume)
      res must be_==(Stream(3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 11))
    }
  }

  "sequenceSink" should {
    import SequencedSinkResponse._
    import Pipe._
    "simple sink" in {
      val s = Stream.from(1).take(11)
      val sink: Sink[Int, Id, SequencedSinkResponse[Unit, Id, Int, Int]] = for {
        _ <- drop[Id, Int](2)
        x <- head[Id, Int]
      } yield (Emit[Unit, Id, Int, Int]((), x.map(Stream(_)).getOrElse(Stream.Empty)))

      val res = (sourceList[Id, Int](s) %= sequenceSink((), (_: Unit) => sink) %%== consume)
      res must be_==(Stream(3, 6, 9))
    }
    "finishes on new state" in {
      val s = Stream.from(1).take(11)
      val sink: Sink[Int, Id, SequencedSinkResponse[Unit, Id, Int, Int]] = for {
        x <- head[Id, Int]
      } yield (Emit[Unit, Id, Int, Int]((), x.map(Stream(_)).getOrElse(Stream.Empty)))

      val res = (sourceList[Id, Int](s) %= sequenceSink((), (_: Unit) => sink) %%== consume)
      res must be_==(s)
    }
    "switch to a conduit" in {
      val s = Stream.from(1).take(11)
      val sink: Sink[Int, Id, SequencedSinkResponse[Unit, Id, Int, Int]] = for {
        _ <- drop[Id, Int](4)
      } yield (StartConduit[Unit, Id, Int, Int](filter(i => i % 2 == 0)))

      val res = (sourceList[Id, Int](s) %= sequenceSink((), (_: Unit) => sink) %%== consume)
      res must be_==(Stream(6, 8, 10))
    }
  }

  "zipping sinks" should {
    "take fewer on left" in {
      val s = Stream.from(0).take(10)
      val res = (sourceList[Id, Int](s) %%== zipSinks(take[Id, Int](4), consume[Id, Int]))
      res must be_==(s.take(4), s)
    }
    "take fewer on right" in {
      val s = Stream.from(0).take(10)
      val res = (sourceList[Id, Int](s) %%== zipSinks(consume[Id, Int], take[Id, Int](4)))
      res must be_==(s, s.take(4))
    }
  }
}

