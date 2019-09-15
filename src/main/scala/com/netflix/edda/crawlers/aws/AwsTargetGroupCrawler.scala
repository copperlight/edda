package com.netflix.edda.crawlers.aws

import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for TargetGroups
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsTargetGroupCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribeTargetGroupsRequest

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator {
      override def next() = {
        val response = backoffRequest {
          ctx.awsClient.elbv2.describeTargetGroups(request.withMarker(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextMarker)
        response.getTargetGroups.asScala
          .map(
            item => Record(item.getTargetGroupName, ctx.beanMapper(item))
          )
          .toList
      }
    }
    it.toList.flatten
  }
}
