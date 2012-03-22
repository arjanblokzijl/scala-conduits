package conduits
package binary

import Source._
import SourceUtil._
import scalaz.effect.IO
import java.io.{FileInputStream, File}
import java.nio.channels.ByteChannel
import resourcet.MonadResource

/**
 * User: arjan
 */

object Binary {

  def sourceFile[F[_]](f: File)(implicit MR: MonadResource[F]): Source[F, java.nio.ByteBuffer] = {
    val str = new FileInputStream(f)
    sourceIO[F, java.nio.ByteBuffer, java.io.FileInputStream](IO(str), _ => IO(str.close()), s => read(s.getChannel))
  }

  def read[F[_]](chan: ByteChannel)(implicit MR: MonadResource[F]): F[SourceIOResult[java.nio.ByteBuffer]] = {
    MR.MO.map(MR.MO.point(java.nio.ByteBuffer.allocate(8*1024)))(buf => chan.read(buf) match {
      case -1 => IOClosed.apply
      case _ => IOOpen(buf)
    })
  }
}
