package conduits

import org.specs2.mutable.Specification
import conduits.CL._
import scalaz.effect.IO
import Conduits._
import org.specs2.ScalaCheck
import resourcet.monadControlIO._
import resourcet.MonadActive._

/**
 * User: arjan
 */

class SourceSpec extends Specification with ScalaCheck {

  "lazyConsume" ! check {(s: Stream[Int]) =>
     val res: Stream[Int] = sourceList[IO, Int](s).lazyConsume.unsafePerformIO
     res must be_==(s)
  }
}
