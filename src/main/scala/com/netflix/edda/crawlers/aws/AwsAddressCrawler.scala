package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeAddressesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record

import scala.collection.JavaConverters._
import scala.collection.mutable

/** crawler for EIP Addresses
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsAddressCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeAddressesRequest

  override def doCrawl()(implicit req: RequestId): mutable.Buffer[Record] =
    backoffRequest {
      ctx.awsClient.ec2
        .describeAddresses(request)
        .getAddresses
        .asScala
        .map(item => Record(item.getPublicIp, ctx.beanMapper(item)))
    }
}
