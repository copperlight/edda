package com.netflix.edda.servlets

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import com.netflix.edda.collections.CollectionManager
import com.typesafe.scalalogging.StrictLogging
import javax.inject.Singleton
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object HealthCheck extends StrictLogging {

  val e = new ScheduledThreadPoolExecutor(1)
  val status = new AtomicInteger(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)

  val runnable: Runnable = new Runnable {

    def run(): Unit = {
      if (!isHealthy) {
        inited = CollectionManager.checkInited

        if (!inited.contains(false)) {
          status.set(HttpServletResponse.SC_OK)
          logger.info(s"HealthCheck OK: ${inited.size} enabled collections are inited")
        }
      }
    }
  }

  @volatile var inited: List[Boolean] = List.empty

  def isHealthy: Boolean = status.get == HttpServletResponse.SC_OK

  e.scheduleWithFixedDelay(runnable, 60, 10, TimeUnit.SECONDS)
}

@Singleton
class HealthCheckServlet extends HttpServlet {

  import HealthCheck._

  override protected def service(
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    response.setStatus(status.get)
    response.setContentType("application/json") // prevent jsonp filter errors
    response.getWriter.flush()
  }
}
