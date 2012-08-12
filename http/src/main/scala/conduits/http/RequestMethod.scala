package conduits
package http

sealed trait RequestMethod

object RequestMethod {
  case object HEAD extends RequestMethod
  case object PUT extends RequestMethod
  case object GET extends RequestMethod
  case object POST extends RequestMethod
  case object DELETE extends RequestMethod
  case object OPTIONS extends RequestMethod
  case object TRACE extends RequestMethod
  case object CONNECT extends RequestMethod
  case class Custom(s: String) extends RequestMethod

  val reqMethodMap =
    Map("HEAD" -> HEAD,
    "PUT" -> PUT,
    "GET" -> GET,
    "POST" -> POST,
    "DELETE" -> DELETE,
    "OPTIONS" -> OPTIONS,
    "TRACE" -> TRACE,
    "CONNECT" -> CONNECT
  )
}
