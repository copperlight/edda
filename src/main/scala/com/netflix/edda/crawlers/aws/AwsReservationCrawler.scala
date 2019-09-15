package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import com.netflix.edda.util.Common

import scala.collection.JavaConverters._

/** crawler for Reservations (ie group of instances, not pre-paid reserved instances)
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsReservationCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeInstancesRequest

  lazy val abortWithoutTags = Common.getProperty("edda.crawler", "abortWithoutTags", name, "false")
  override def doCrawl()(implicit req: RequestId) = {
    var tagCount = 0
    val list = backoffRequest { ctx.awsClient.ec2.describeInstances(request).getReservations }.asScala
      .map(item => {
        tagCount += item.getInstances.asScala.map(_.getTags.size).sum
        Record(item.getReservationId, ctx.beanMapper(item))
      })
      .toSeq
    if (tagCount == 0 && abortWithoutTags.get.toBoolean) {
      throw new java.lang.RuntimeException("no tags found for " + name + ", ignoring crawl results")
    }
    list
  }
}
