package resourcet

import org.specs2.mutable.Specification
import scalaz.effect.{IORef, IO}
import resource._

/**
 * User: arjan
 */

class ResourceTSpec extends Specification {
  val mr = resourceTMonadResource[IO]

  "ResourceT" should {

    "cleanup registered actions when running" in {
      val ioCounter: IO[IORef[Int]] = IO.newIORef(0)
      val result = ioCounter.flatMap(counter => {
        val ww: ResourceT[IO, (ReleaseKey, Unit)] = mr.allocate(atomicModifyIORef(counter)(i => ((i + 1), ()))
          , (u: Unit) => atomicModifyIORef(counter)(i => ((i + 2), ())))
        val res: IO[Unit] = runResourceT(ww.flatMap(_ => resourceTMonad[IO].point(())))
        res.flatMap(_ => counter.read)
      })

      result.unsafePerformIO mustEqual(3)
    }
  }
}
