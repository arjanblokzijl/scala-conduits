package conduits
package binary

import pipes._
import Pipe._
import SourceFunctions._
import scalaz.effect.IO
import java.io.{FileInputStream, File}
import resourcet.MonadResource

/**
 * User: arjan
 */

object Binary {

  val bufferSize = 8*1024

  def sourceFile[F[_]](f: File, chunkSize: Int = byteString.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIOInputStream(IO(new FileInputStream(f)))

  def sourceIOInputStream[F[_]](alloc: IO[FileInputStream], chunkSize: Int = byteString.DefaultChunkSize)(implicit MR: MonadResource[F]): Source[F, ByteString] =
    sourceIO[F, ByteString, java.io.FileInputStream](alloc, s => IO(s.close()), s => {
      MR.MO.map(MR.MO.liftIO(byteString.getContents(s.getChannel, chunkSize)))(bs =>
        if (bs.isEmpty) IOClosed.apply
        else IOOpen(bs))
    })
}
