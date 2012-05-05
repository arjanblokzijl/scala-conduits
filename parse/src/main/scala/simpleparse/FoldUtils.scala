package simpleparse

/**
 * User: arjan
 */

object FoldUtils {
  private[simpleparse] val ToNone: ((=> Any) => None.type) = x => None
  private[simpleparse] val ToNone1: (Any => None.type) = x => None
  private[simpleparse] val ToNone2: ((=> Any, => Any) => None.type) = (x, y) => None
  private[simpleparse] val ToNone3: ((=> Any, => Any, => Any) => None.type) = (x, y, z) => None

}
