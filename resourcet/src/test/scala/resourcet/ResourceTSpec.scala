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
      val result = for {
        counter <- IO.newIORef(0)
        val w = mr.allocate(atomicModifyIORef(counter)(i => ((i + 1), ()))
                           , (u: Unit) => atomicModifyIORef(counter)(i => ((i + 2), ())))

        _ <- runResourceT(w)
        res <- counter.read
      } yield (res)

      result.unsafePerformIO mustEqual(3)
    }
  }
}
