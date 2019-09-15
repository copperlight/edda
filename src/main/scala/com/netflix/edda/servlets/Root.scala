package com.netflix.edda.servlets

import com.netflix.config.ConfigurationManager
import com.netflix.edda.util.Common.toPrettyJson
import com.typesafe.scalalogging.StrictLogging
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.apache.commons.configuration.Configuration

import scala.collection.JavaConverters._
import scala.collection.immutable.SortedMap

@Singleton
class Root extends HttpServlet with StrictLogging {

  private val config: Configuration = ConfigurationManager.getConfigInstance

  private val stack = System.getenv("NETFLIX_STACK").replace("_", "-")
  private val region = System.getenv("EC2_REGION")
  private val env = System.getenv("NETFLIX_ENVIRONMENT")

  private val baseUrl =
    if (stack == "local")
      "http://localhost:7101"
    else
      s"http://edda-$stack.$region.$env.netflix.net"

  private val enabledPattern = """edda\.collection\.(aws|group|netflix|view)\.(.+)\.enabled"""
  private val enabledCompiled = enabledPattern.r

  private val collections = collection.mutable.Map[String, Any]()

  for (k <- config.getKeys.asScala) {
    if (k.matches(enabledPattern)) {
      val enabledCompiled(collType, collName) = k
      val statusKey = s"$collType.$collName"
      collections += statusKey -> None
    }
  }

  def getProperty(coll: String, prop: String): String = {
    val defaultProp = prop.replace(s"$coll.", "")
    if (Option(config.getString(prop)).isDefined)
      config.getString(prop)
    else if (Option(config.getString(defaultProp)).isDefined)
      config.getString(defaultProp)
    else
      s"undefined properties: $prop, $defaultProp"
  }

  def mkLink(coll: String): String = {
      s"$baseUrl/api/v2/${coll.replace('.', '/')}"
  }

  for (k <- collections.keys) {
    val status = Map[String, String](
      "link"             -> mkLink(k),
      "enabled"          -> config.getString(s"edda.collection.$k.enabled"),
      "refresh.ms"       -> getProperty(k, s"edda.collection.$k.refresh"),
      "cache.refresh.ms" -> getProperty(k, s"edda.collection.$k.cache.refresh")
    )
    collections.update(k, status)
  }

  private val sortedCollections = SortedMap(collections.toSeq: _*)

  override protected def service(
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    response.setContentType("application/json")
    val out = response.getWriter

    val payload = Map(
      "description"   -> "Edda is an AWS API caching service",
      "documentation" -> "https://go.prod.netflix.net/eddaapidocs",
      "baseUrl"       -> baseUrl,
      "collections"   -> sortedCollections
    )

    out.println(toPrettyJson(payload))
  }
}