package simpleparse

import scalaz.ApplicativePlus


object Combinator extends CombinatorFunctions

//TODO either keep this, or only the specific parser ones defined in Parser itself.
trait CombinatorFunctions {

  /**
   * `choice` tries to apply the actions in the given list in order,
   * until one of them succeeds. Returns the value of the succeeding
   * action.
   */
  def choice[F[_], A](ps: Seq[F[A]])(implicit F: ApplicativePlus[F]): F[A] =
    ps.foldLeft(F.empty[A])((a, b) => F.plus(a, b))

  /**
   * `option` tries to apply action `p`. If `p` fails without
   * consuming input, it returns the value `x`, otherwise the value
   * returned by `p`.
   */
  def option[F[_], A](x: A, p: F[A])(implicit F: ApplicativePlus[F]): F[A] =
    F.plus(p, F.point(x))

  /**
   * `many1` applies the action `p` one or more times. Returns
   * a list of the returned values of `p`.
   */
  def many1[F[_], A](p: F[A])(implicit F: ApplicativePlus[F]): F[List[A]] =
     F.map2(p, F.many(p))((a, b) => a :: b)

}
