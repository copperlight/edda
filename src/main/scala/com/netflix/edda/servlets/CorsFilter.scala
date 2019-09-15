package com.netflix.edda.servlets

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Singleton
class CorsFilter extends Filter {
  override def init(filterConfig: FilterConfig): Unit = {}

  override def doFilter(
    servletRequest: ServletRequest,
    servletResponse: ServletResponse,
    chain: FilterChain
  ): Unit = {
    val req = servletRequest.asInstanceOf[HttpServletRequest]
    val res = servletResponse.asInstanceOf[HttpServletResponse]

    // http://www.html5rocks.com/en/tutorials/cors/
    Option(req.getHeader("Origin")).foreach { origin =>
      res.addHeader("Access-Control-Allow-Origin", origin)
    }

    Option(req.getHeader("Access-Control-Request-Method")).foreach { _ =>
      res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
    }

    Option(req.getHeader("Access-Control-Request-Headers")).foreach { value =>
      res.addHeader("Access-Control-Allow-Headers", value)
    }

    // https://www.fastly.com/blog/caching-cors
    res.addHeader("Vary", "Origin")

    chain.doFilter(req, res)
  }

  override def destroy(): Unit = {}
}
