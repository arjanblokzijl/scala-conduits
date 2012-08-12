package conduits.http

/**
 * User: arjan
 */

object RequestApp extends App {
  val req = HttpRequest.httpGet("www.google.com")
  val conn = Connection(req)
  val result = conn.execute.unsafePerformIO()
  println("result is " + result.toString)
}
