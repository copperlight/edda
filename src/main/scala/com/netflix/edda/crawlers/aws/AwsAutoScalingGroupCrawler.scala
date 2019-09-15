package com.netflix.edda.crawlers.aws

import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import com.netflix.edda.util.Common
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for AutoScalingGroups
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsAutoScalingGroupCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribeAutoScalingGroupsRequest
  request.setMaxRecords(50)

  lazy val abortWithoutTags = Common.getProperty("edda.crawler", "abortWithoutTags", name, "false")
  override def doCrawl()(implicit req: RequestId) = {
    var tagCount = 0
    val it = new AwsIterator() {
      def next() = {
        val response = backoffRequest {
          ctx.awsClient.asg.describeAutoScalingGroups(request.withNextToken(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextToken)
        response.getAutoScalingGroups.asScala
          .map(item => {
            tagCount += item.getTags.size
            Record(
              item.getAutoScalingGroupName,
              new DateTime(item.getCreatedTime),
              ctx.beanMapper(item)
            )
          })
          .toList
      }
    }
    val list = it.toList.flatten
    if (tagCount == 0) {
      if (abortWithoutTags.get.toBoolean) {
        throw new java.lang.RuntimeException(
          "no tags found for any record in " + name + ", ignoring crawl results"
        )
      } else if (logger.isWarnEnabled)
        logger.warn(
          s"$req no tags found for any record in $name.  If you expect at least one tag then set: edda.crawler.$name.abortWithoutTags=true"
        )
    }
    list
  }
}
