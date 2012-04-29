package conduits

import scalaz.DList
import scalaz.syntax.SyntaxV

/**
 * Some extra DList methods, perhaps useful to include in the std scalaz lib.
 */
trait DListFunctions {

  def take[A](n: Int, dl: DList[A]): DList[A] = {
    def go(count: Int, acc: DList[A], rem: DList[A]): DList[A] =
      if (count <= 0) acc
      else
        rem.uncons(acc, (h, t) => go(count -1, acc :+ h, t))

    go(n, DList(), dl)
  }

  def takeWhile[A](f: A => Boolean, dl: DList[A]): DList[A] = {
    def go(f: A => Boolean, acc: DList[A], rem: DList[A]): DList[A] =
      rem.uncons(acc, (h, t) => go(f, if (f(h)) acc :+ h else acc, t))

    go(f, DList(), dl)
  }

  def dropWhile[A](f: A => Boolean, dl: DList[A]): DList[A] = {
    def go(f: A => Boolean, acc: DList[A]): DList[A] =
      acc.uncons(acc, (h, t) => go(f, if (f(h)) t else t :+ h))

    go(f, dl)
  }

  def drop[A](n: Int, dl: DList[A]): DList[A] = {
    def go(count: Int, acc: DList[A]): DList[A] = {
      if (count <= 0) acc
      else
        acc.uncons(acc, (h, t) => go(count -1, t))
    }
    go(n, dl)
  }

  def empty[A](dl: DList[A]): Boolean = dl.uncons(true, (h, t) => false)
}

object dList extends DListFunctions

trait DListV[A] extends SyntaxV[DList[A]] {
  final def take(n: Int): DList[A] = dList.take(n, self)
  final def drop(n: Int): DList[A] = dList.drop(n, self)
  final def dropWhile(f: A => Boolean): DList[A] = dList.dropWhile(f, self)
  final def takeWhile(f: A => Boolean): DList[A] = dList.takeWhile(f, self)
}

trait ToDListV {
  implicit def ToDListVFromDList[A](dl: DList[A]): DListV[A] = new DListV[A] {
    def self = dl
  }
}