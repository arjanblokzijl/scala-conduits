package conduits
package empty

import scalaz.{Semigroup, Monoid, Monad, Functor}


/**
 * The uninhabited type.
 */
case class Void(z: Void)

object Void {

  /**
   * Logical reasoning of type 'ex contradictione sequitur quodlibet'
   */
  def absurd[A](z: Void): A = absurd(z)

  def vacuous[F[_], A](fa: F[Void], z: Void)(implicit F: Functor[F]): F[A] = F.map(fa)(absurd(z))

  implicit def voidSemiGroup: Semigroup[Void] = new Semigroup[Void] {
    def append(f1: Void, f2: => Void) = f1
  }
}
