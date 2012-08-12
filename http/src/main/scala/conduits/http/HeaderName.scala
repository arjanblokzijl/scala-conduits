package conduits
package http


sealed trait HeaderName

object HeaderName {
  case object HdrCacheControl extends HeaderName
  case object HdrConnection extends HeaderName
  case object HdrDate extends HeaderName
  case object HdrPragma extends HeaderName
  case object HdrTransferEncoding extends HeaderName
  case object HdrUpgrade extends HeaderName
  case object HdrVia extends HeaderName
  //Request Headers
  case object HdrAccept extends HeaderName
  case object HdrAcceptCharset extends HeaderName
  case object HdrAcceptEncoding extends HeaderName
  case object HdrAcceptLanguage extends HeaderName
  case object HdrAuthorization extends HeaderName
  case object HdrCookie extends HeaderName
  case object HdrExpect extends HeaderName
  case object HdrFrom extends HeaderName
  case object HdrHost extends HeaderName
  case object HdrIfModifiedSince extends HeaderName
  case object HdrIfMatch extends HeaderName
  case object HdrIfNoneMatch extends HeaderName
  case object HdrIfRange extends HeaderName
  case object HdrIfUnmodifiedSince extends HeaderName
  case object HdrMaxForwards extends HeaderName
  case object HdrProxyAuthorization extends HeaderName
  case object HdrRange extends HeaderName
  case object HdrReferer extends HeaderName
  case object HdrUserAgent extends HeaderName
   //Response Headers
  case object HdrAge extends HeaderName
  case object HdrLocation extends HeaderName
  case object HdrProxyAuthenticate extends HeaderName
  case object HdrPublic extends HeaderName
  case object HdrRetryAfter extends HeaderName
  case object HdrServer extends HeaderName
  case object HdrSetCookie extends HeaderName
  case object HdrTE extends HeaderName
  case object HdrTrailer extends HeaderName
  case object HdrVary extends HeaderName
  case object HdrWarning extends HeaderName
  case object HdrWWWAuthenticate extends HeaderName
   //Entity Headers
  case object HdrAllow extends HeaderName
  case object HdrContentBase extends HeaderName
  case object HdrContentEncoding extends HeaderName
  case object HdrContentLanguage extends HeaderName
  case object HdrContentLength extends HeaderName
  case object HdrContentLocation extends HeaderName
  case object HdrContentMD5 extends HeaderName
  case object HdrContentRange extends HeaderName
  case object HdrContentType extends HeaderName
  case object HdrETag extends HeaderName
  case object HdrExpires extends HeaderName
  case object HdrLastModified extends HeaderName
  //MIME entity headers (for sub-parts)
  case object HdrContentTransferEncoding extends HeaderName
  //Allows for unrecognised or experimental headers.
  case class HdrCustom(s: String) extends HeaderName //not in header map below.

  val headerMap: Map[String, HeaderName] =
    Map("Cache-Control" ->      HdrCacheControl
      ,"Connection" ->          HdrConnection
      ,"Date" ->                HdrDate
      ,"Pragma"->               HdrPragma
      ,"Transfer-Encoding"->    HdrTransferEncoding
      ,"Upgrade"->              HdrUpgrade
      ,"Via"->                  HdrVia
      ,"Accept"->               HdrAccept
      ,"Accept-Charset"->       HdrAcceptCharset
      ,"Accept-Encoding"->      HdrAcceptEncoding
      ,"Accept-Language"->      HdrAcceptLanguage
      ,"Authorization"->        HdrAuthorization
      ,"Cookie"->               HdrCookie
      ,"Expect"->               HdrExpect
      ,"From"->                 HdrFrom
      ,"Host"->                 HdrHost
      ,"If-Modified-Since"->    HdrIfModifiedSince
      ,"If-Match"->             HdrIfMatch
      ,"If-None-Match"->        HdrIfNoneMatch
      ,"If-Range"->             HdrIfRange
      ,"If-Unmodified-Since"->  HdrIfUnmodifiedSince
      ,"Max-Forwards"->         HdrMaxForwards
      ,"Proxy-Authorization"->  HdrProxyAuthorization
      ,"Range"->                HdrRange
      ,"Referer"->              HdrReferer
      ,"User-Agent"->           HdrUserAgent
      ,"Age"->                  HdrAge
      ,"Location"->             HdrLocation
      ,"Proxy-Authenticate"->   HdrProxyAuthenticate
      ,"Public"->               HdrPublic
      ,"Retry-After"->          HdrRetryAfter
      ,"Server"->               HdrServer
      ,"Set-Cookie"->           HdrSetCookie
      ,"TE"->                   HdrTE
      ,"Trailer"->              HdrTrailer
      ,"Vary"->                 HdrVary
      ,"Warning"->              HdrWarning
      ,"WWW-Authenticate"->     HdrWWWAuthenticate
      ,"Allow"->                HdrAllow
      ,"Content-Base"->         HdrContentBase
      ,"Content-Encoding"->     HdrContentEncoding
      ,"Content-Language"->     HdrContentLanguage
      ,"Content-Length"->       HdrContentLength
      ,"Content-Location"->     HdrContentLocation
      ,"Content-MD5"->          HdrContentMD5
      ,"Content-Range"->        HdrContentRange
      ,"Content-Type"->         HdrContentType
      ,"ETag"->                 HdrETag
      ,"Expires"->              HdrExpires
      ,"Last-Modified"->        HdrLastModified
      ,"Content-Transfer-Encoding"-> HdrContentTransferEncoding
    )
}
