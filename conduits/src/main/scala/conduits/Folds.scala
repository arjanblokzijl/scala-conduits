package conduits

/**
 * User: arjan
 */

object Folds {
  private[conduits] val ToNone: ((=> Any) => None.type) = x => None
  private[conduits] val ToNone1: (Any => None.type) = x => None
  private[conduits] val ToNone2: ((=> Any, => Any) => None.type) = (x, y) => None
  private[conduits] val ToNone3: ((=> Any, => Any, => Any) => None.type) = (x, y, z) => None

}
