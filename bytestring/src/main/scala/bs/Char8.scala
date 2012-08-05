package bs


/**
 * Utility to manipulate 'ByteString's using 'Char' operations. All Chars will be
 * truncated to 8 bits.
 */
object Char8 {

  /**
   * Encode the given string into a ByteString.
   */
  def pack(s: String): ByteString = {
    val buff = s.toCharArray
    val res = new Array[Byte](buff.length << 1)
    var i = 0
    while (i < buff.length) {
      //ugly, but fast
      val bpos = i << 1
      res(bpos) = ((buff(i) & 0xFF00) >> 8).toByte
      res(bpos + 1) = (buff(i) & 0x00FF).toByte
      i += 1
    }
    ByteString(res)
  }

  def unpack(bs: ByteString): String = {
    val res = new Array[Char](bs.length >> 1)
    var i = 0
    while (i < res.length) {
      val bpos = i << 1
      res(i) = (((bs(bpos) & 0x00FF) << 8) + ((bs(bpos + 1) & 0x00FF))).toChar
      i += 1
    }
    new String(res)
  }
}


