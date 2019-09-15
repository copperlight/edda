/*
 * Copyright 2012-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.edda.actors

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger

import com.netflix.servo.DefaultMonitorRegistry
import com.netflix.servo.monitor.Monitors

import scala.concurrent.ExecutionContext

class NamedThreads(name: String) extends ThreadFactory {
  val number = new AtomicInteger(0)

  def newThread(r: Runnable): Thread = {
    new Thread(r, s"$name-${number.incrementAndGet()}")
  }
}

object ThreadPools {
  val procs: Int = Runtime.getRuntime.availableProcessors

  def mkThreadPool(nThreads: Int, name: String): ExecutorService = {
    val pool = Executors.newFixedThreadPool(nThreads, new NamedThreads(name))

    DefaultMonitorRegistry
      .getInstance()
      .register(
        Monitors
          .newThreadPoolMonitor(s"edda.$name", pool.asInstanceOf[ThreadPoolExecutor])
      )

    pool
  }

  /** Primary Thread Pools
    *
    * these are used by core actor classes to perform specific work while preventing the main
    * actor event loop from blocking.
    *
    * the crawler pool is set high, because there are typically 46 enabled collections in a
    * standard edda deployment.
    *
    * the query pool is set high, because it should follow the number of tomcat threads serving
    * http traffic.
    *
    * separately, the actor system thread pool is configured with the following system properties,
    * defined in root/etc/default/ezconfig:
    *
    * - actors.corePoolSize - default is the greater of 4 or 2 * available processors
    * - actors.maxPoolSize - default is 256
    *
    * to avoid message passing failures, due to inadvertent event loop blocking, the corePoolSize
    * should be set to about twice the number of collections running in the system. there are 46
    * collections, each of which has four associated actors (collection, crawler, refresher, and
    * processor). this will provide some overhead for growth, but if the number of collections
    * grows substantially, it will need to be re-tuned.
    *
    */
  val crawlerPool: ExecutorService = mkThreadPool(2 * procs, "crawler-pool")
  val electorPool: ExecutorService = mkThreadPool(2, "elector-pool")
  val observerPool: ExecutorService = mkThreadPool(2, "observer-pool")
  val purgePool: ExecutorService = mkThreadPool(1, "purge-pool")
  val queryPool: ExecutorService = mkThreadPool(2 * procs, "query-pool")

  /** Secondary Thread Pools
    *
    * these pools were originally defined throughout the crawler code base. with the addition of
    * the updateInProgress flag that prevents multiple Crawl messages from being sent to actors
    * and the change to send all Crawling activity to a crawler thread pool as futures, these
    * should no longer be necessary to avoid blocking the actor event loop.
    *
    * until this can be refactored, collecting the thread pool configurations in one place makes
    * it easy to give them readable names and tune their values.
    *
    * they are lazy, because not all of these crawlers are active for every stack in every region.
    *
    */
  lazy val hostedRecordPool: ExecutorService = mkThreadPool(2, "hosted-record-pool")
  lazy val instanceHealthPool: ExecutorService = mkThreadPool(2, "instance-health-pool")
  lazy val tgHealthPool: ExecutorService = mkThreadPool(2, "tg-health-pool")
}

object CrawlerExecutionContext {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(ThreadPools.crawlerPool)
}

object ElectorExecutionContext {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(ThreadPools.electorPool)
}

object ObserverExecutionContext {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(ThreadPools.observerPool)
}

object PurgeExecutionContext {
  implicit var ec: ExecutionContext =
    ExecutionContext.fromExecutorService(ThreadPools.purgePool)
}

object QueryExecutionContext {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(ThreadPools.queryPool)
}
