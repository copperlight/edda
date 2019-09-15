package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeReservedInstancesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/** crawler for ReservedInstance (ie pre-paid instances)
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsReservedInstanceCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeReservedInstancesRequest

  override def doCrawl()(implicit req: RequestId) =
    backoffRequest { ctx.awsClient.ec2.describeReservedInstances(request).getReservedInstances }.asScala
      .map(
        item =>
          Record(item.getReservedInstancesId, new DateTime(item.getStart), ctx.beanMapper(item))
      )
      .toSeq
}
