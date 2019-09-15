package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record

import scala.collection.JavaConverters._

/** crawler for ReservedInstancesOfferings
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsReservedInstancesOfferingCrawler(val name: String, val ctx: AwsCrawler.Context)
  extends Crawler {
  val request = new DescribeReservedInstancesOfferingsRequest

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator() {
      def next() = {
        val response = backoffRequest {
          ctx.awsClient.ec2
            .describeReservedInstancesOfferings(request.withNextToken(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextToken)
        response.getReservedInstancesOfferings.asScala
          .map(item => {
            Record(item.getReservedInstancesOfferingId, ctx.beanMapper(item))
          })
          .toList
      }
    }
    it.toList.flatten
  }
}
