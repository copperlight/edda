package com.netflix.edda.aws

import java.util.concurrent.TimeUnit

import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest
import com.netflix.edda.crawlers.AwsCrawler
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

/** List Availability Zones for the AWS account.
  *
  * Used during initialization to gather the current list of Availability Zones for
  * the account, so that collections which support zone sharding can take advantage
  * of it to improve crawling speed up to 3X.
  *
  * @param ctx AwsCrawler Context
  */
class AvailabilityZones(val ctx: AwsCrawler.Context) extends StrictLogging {

  private val zones = describeZones()

  private val errorWait = Duration(1, TimeUnit.MINUTES).toMillis

  private def describeZones(): List[String] = {
    try {
      val result = ctx.awsClient.ec2
        .describeAvailabilityZones(new DescribeAvailabilityZonesRequest())
        .getAvailabilityZones
        .asScala
        .map(_.getZoneName)
        .toList

      logger.info(s"zones=$result")

      result
    } catch {
      case e: Exception =>
        logger.error(s"failed to get availability zones: ${e.getMessage}")
        Thread.sleep(errorWait)
        describeZones()
    }
  }

  def get: List[String] = zones
}
