package com.netflix.edda.crawlers.aws

import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for ASG Activities
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsScalingActivitiesCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribeScalingActivitiesRequest
  request.setMaxRecords(50)

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator() {
      def next() = {
        val response = backoffRequest {
          ctx.awsClient.asg.describeScalingActivities(request.withNextToken(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextToken)
        response.getActivities.asScala
          .map(item => {
            Record(item.getActivityId, ctx.beanMapper(item))
          })
          .toList
      }
    }
    it.toList.flatten
  }
}
