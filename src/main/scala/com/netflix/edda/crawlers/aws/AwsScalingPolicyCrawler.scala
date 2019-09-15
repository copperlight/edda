package com.netflix.edda.crawlers.aws

import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for ASG Policies
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsScalingPolicyCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribePoliciesRequest
  request.setMaxRecords(50)

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator() {
      def next() = {
        val response = backoffRequest {
          ctx.awsClient.asg.describePolicies(request.withNextToken(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextToken)
        response.getScalingPolicies.asScala
          .map(item => {
            Record(item.getPolicyName, ctx.beanMapper(item))
          })
          .toList
      }
    }
    it.toList.flatten
  }
}
