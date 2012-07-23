package conduits
package network

import java.nio.channels._
import scala.collection.JavaConverters._
import scalaz.effect.IO

class NioSelector(serverChannel: ServerSocketChannel) extends Runnable {
  val selector = Selector.open
  serverChannel.configureBlocking(false)

  def registerServer[F[_]](callback: SocketChannel => F[Unit]): Unit = {
    def loop: Unit = {
      val n = selector.select
        if (n == 0) loop
        val keys = selector.selectedKeys.asScala.toList
        println("got keys " + keys)
        selector.selectedKeys().clear()
        keys.foreach(key => {
          if (key.isAcceptable) {
            val server = key.channel.asInstanceOf[ServerSocketChannel]
            val chan = server.accept
            println("got channel for accept " + chan)
            if (chan == null) loop
            registerChannel(chan, SelectionKey.OP_READ)
          }
          if (key.isReadable) {
            val readableChan = key.channel.asInstanceOf[SocketChannel]
            println("got channel for for reading " + readableChan)
            callback(readableChan)
          }
        })
//        keys.foreach(_.interestOps(0))
        loop
      }
    serverChannel.register(selector, SelectionKey.OP_ACCEPT)
    loop
  }

  def run: Unit = {
    def loop: Unit = {
      select(loop _)
        loop
      }
    loop
  }

  def select(loop: () => Unit) {
    serverChannel.register(selector, SelectionKey.OP_ACCEPT)
    val n = selector.select
    if (n == 0) loop()
    val keys = selector.selectedKeys.asScala.toList
    println("got keys " + keys)
    selector.selectedKeys().clear()
    keys.foreach(_.interestOps(0))
    keys.foreach(key => {
      val callback: () => Any = key.attachment.asInstanceOf[() => Any]
      callback()
    })
  }

  def accept[F[_]](callback: SocketChannel => F[Unit]): IO[Unit] = {
    println("in accept")
    IO{
      serverChannel.register(selector, SelectionKey.OP_ACCEPT, {
        () => {
          val socket = serverChannel.accept()
          socket.configureBlocking(false)
          socket.register(selector, SelectionKey.OP_READ, () => {callback})}})
      ()
    }
  }

  def read[F[_]](socket: SocketChannel, callback: SocketChannel => F[Unit]): List[F[Unit]] = {
    registerChannel(socket, SelectionKey.OP_READ)
    selector.select
    val keys = selector.selectedKeys.asScala.toList
    selector.selectedKeys().clear()
    keys.foreach(_.interestOps(0))
    keys.map(key => {
      val socket = serverChannel.accept
      println("got channel for read " + socket)
      callback(socket)
    })
  }


  private def register[F[_]](chan: SelectableChannel, op: Int, callback: SocketChannel => F[Unit]): Unit = {
    if (chan != null) {
      chan.configureBlocking(false)
      chan.register(selector, op, callback)
    }
  }

  private def registerChannel(chan: SelectableChannel, op: Int) = {
    if (chan != null) {
      chan.configureBlocking(false)
      chan.register(selector, op)
    }
  }

}
