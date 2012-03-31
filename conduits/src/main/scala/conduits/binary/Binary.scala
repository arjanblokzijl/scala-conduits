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

  def sourceFile[F[_]](f: File)(implicit MR: MonadResource[F]): Source[F, SByteString] =
    sourceIOInputStream(IO(new FileInputStream(f)))

  def sourceIOInputStream[F[_]](alloc: IO[FileInputStream])(implicit MR: MonadResource[F]): Source[F, SByteString] =
    sourceIO[F, SByteString, java.io.FileInputStream](alloc, s => IO(s.close()), s => {
      MR.MO.map(MR.MO.liftIO(byteString.getContents(s.getChannel)))(bs =>
        if (bs.isEmpty) IOClosed.apply
        else IOOpen(bs))
    })
}
