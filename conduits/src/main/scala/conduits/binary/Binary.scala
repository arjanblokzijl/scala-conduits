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

  val bufferSize = 8*1024

  def sourceFile[F[_]](f: File)(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIOInputStream(IO(new FileInputStream(f)))

  def sourceIOInputStream[F[_]](alloc: IO[FileInputStream])(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIO[F, ByteString, java.io.FileInputStream](alloc, s => IO(s.close()), s => {
      MR.MO.map(MR.MO.liftIO(byteString.getContents(s.getChannel)))(bs =>
        if (bs.isEmpty) IOClosed.apply else IOOpen(bs))
    })
}
