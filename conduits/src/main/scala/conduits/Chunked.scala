package conduits


trait Chunked[A, B <: IndexedSeq[A]] {

  def fold[Z](empty: => Z, chunk: (=> B, => Chunked[A, B]) => Z): Z

  def foldrChunks[Z](z: => Z)(f: (B, => Z) => Z): Z = {
    import scalaz.Free._
    import scalaz.std.function._
    def go(bs: => Chunked[A, B], z: => Z, f: (B, => Z) => Z): Trampoline[Z] =
      fold(return_(z)
           , (c, cs) => go(cs, z, f) map (x => f(c, x)))

    go(this, z, f).run
  }

  def isEmpty: Boolean = fold(true, (_, _) => false)
}
