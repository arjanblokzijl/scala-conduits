package conduits

import scalaz.{Monoid, Functor, Monad}
import sink._

/**
* User: arjan
*/
sealed trait Source[F[_], A] {
  import Source._

  def map[B](f: (A) => B)(implicit M: Monad[F]): Source[F, B] = fold(
     open = (source, close, a) => Open(source.map(f), close, f(a))
     , close = Closed.apply[F, B]
     , sourceM = (msrc, close) => SourceM.apply(M.map(msrc)(s => s.map(f)), close))

  def fold[Z](open: (=> Source[F, A], => F[Unit], => A) => Z
              , close: => Z
              , sourceM: (=> F[Source[F, A]], => F[Unit]) => Z): Z

  def %= [B](conduit: Conduit[A, F, B])(implicit M: Monad[F]): Source[F, B] = Conduits.normalFuseLeft(this, conduit)

  def >>== [B](sink: Sink[A, F, B])(implicit M: Monad[F]): F[B] = Conduits.normalConnect(this, sink)

  def append(that: Source[F, A])(implicit M: Monad[F]): Source[F, A] = {
    def go(f1: Source[F, A], f2 : Source[F, A]): Source[F, A] = (f1, f2) match {
      case (x, Closed()) => x
      case (Closed(), y) => y
      case (Open(next, close, a), y) => Open[F, A](go(next, y), close, a)
      case (SourceM(msrc, close), y) => SourceM[F, A](M.map(msrc)(src => go(src, y)), close)
    }
    go(this, that)
  }

  def sourceClose(implicit M: Monad[F]): F[Unit] = this match {
    case Closed() => M.point(())
    case Open(_, close, _) => close
    case SourceM(_, close) => close
  }

  def zip[B](that: Source[F, B])(implicit M: Monad[F]): Source[F, (A, B)] = {
    def go(f1: Source[F, A], f2 : Source[F, B]): Source[F, (A, B)] = (f1, f2) match {
      case (Closed(), Closed()) => Closed.apply
      case (Closed(), Open(_, close, _)) => SourceM[F, (A, B)](M.bind(close)(_ => M.point(Closed.apply)), close)
      case (Open(_, close, _), Closed()) => SourceM[F, (A, B)](M.bind(close)(_ => M.point(Closed.apply)), close)
      case (Closed(), SourceM(_, close)) => SourceM[F, (A, B)](M.bind(close)(_ => M.point(Closed.apply)), close)
      case (SourceM(_, close), Closed()) => SourceM[F, (A, B)](M.bind(close)(_ => M.point(Closed.apply)), close)
      case (SourceM(mx, closex), SourceM(my, closey)) => SourceM[F, (A, B)](M.map2(mx, my)((a, b) => go(a, b)), M.bind(closex)(_ => closey))
      case (SourceM(mx, closex), y@Open(_, closey, _)) => SourceM[F, (A, B)](M.map(mx)(x => go(x, y)), M.bind(closex)(_ => closey))
      case (x@Open(_, closex, _), SourceM(my, closey)) => SourceM[F, (A, B)](M.map(my)(y => go(x, y)), M.bind(closex)(_ => closey))
      case (Open(srcx, closex, x),Open(srcy, closey, y)) => Open(go(srcx, srcy), M.bind(closex)(_ => closey), (x, y))
      case _ => sys.error("")
    }
    go(this, that)
  }
}

object Source {
  import FoldUtils._
  object Open {
    def apply[F[_], A](s: => Source[F, A], c: => F[Unit], a: => A) = new Source[F, A] {
      def fold[Z](open: (=> Source[F, A], => F[Unit], => A) => Z, close: => Z, sourceM: (=> F[Source[F, A]], => F[Unit]) => Z) = open(s, c, a)
    }
    def unapply[F[_], A](s: Source[F, A]): Option[(Source[F, A], F[Unit], A)] = {
      s.fold((s, c, a) => Some(s, c, a), None, ToNone2)
    }
  }

  object Closed {
    def apply[F[_], A] = new Source[F, A] {
      def fold[Z](open: (=> Source[F, A], => F[Unit], => A) => Z, close: => Z, sourceM: (=> F[Source[F, A]], => F[Unit]) => Z) = close
    }
    def unapply[F[_], A](s: Source[F, A]): Boolean = {
      s.fold((_, _, _) => false, true, (_, _) => false)
    }
  }

  object SourceM {
    def apply[F[_], A](msrc: => F[Source[F, A]], c: => F[Unit]) = new Source[F, A] {
      def fold[Z](open: (=> Source[F, A], => F[Unit], => A) => Z, close: => Z, sourceM: (=> F[Source[F, A]], => F[Unit]) => Z) = sourceM(msrc, c)
    }
    def unapply[F[_], A](s: Source[F, A]): Option[(F[Source[F, A]], F[Unit])] = {
      s.fold(ToNone3, None, (s, c) => Some(s, c))
    }
  }
}

trait SourceInstances {
  implicit def sourceFunctor[F[_]](implicit M0: Monad[F]): Functor[({type l[a] = Source[F, a]})#l] = new Functor[({type l[a] = Source[F, a]})#l] {
     def map[A, B](fa: Source[F, A])(f: (A) => B): Source[F, B] = fa map f
  }

  implicit def sourceMonoid[A, F[_]](implicit M0: Monad[F]): Monoid[Source[F, A]] = new SourceMonoid[A, F] {
    implicit val M = M0
  }
}

private[conduits] trait SourceMonoid[A, F[_]] extends Monoid[Source[F, A]] {
  import Source._
  implicit val M: Monad[F]

  def append(f1: Source[F, A], f2: => Source[F, A]): Source[F, A] = f1 append f2

  def zero = Closed.apply[F, A]
}

object source extends SourceInstances with SourceFunctions

