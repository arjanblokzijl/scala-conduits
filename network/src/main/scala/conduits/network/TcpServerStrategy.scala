package conduits.network

import concurrent.{JavaConversions, FutureTaskRunner}
import java.util.concurrent._
import java.util.concurrent.Executors._
import scalaz.concurrent.Strategy

/**
 * User: arjan
 */

object TcpServerStrategy {

  val numCores = Runtime.getRuntime().availableProcessors()
  val maxPoolsize = 30
  val keepAliveTime = 180L
  val workQueue = new LinkedBlockingQueue[Runnable]
  val threadFactory =
    new ThreadFactory {
      def newThread(r: Runnable) = {
        val t = defaultThreadFactory.newThread(r)
        t.setDaemon(true)
        t
      }
    }

  val executor = new ThreadPoolExecutor(numCores,
                                    maxPoolsize,
                                    keepAliveTime,
                                    TimeUnit.SECONDS,
                                    workQueue,
                                    threadFactory)

  implicit val TcpServerExecutorService: ExecutorService = executor

  implicit val DefaultServerStrategy = Strategy.Executor(TcpServerExecutorService)

}
