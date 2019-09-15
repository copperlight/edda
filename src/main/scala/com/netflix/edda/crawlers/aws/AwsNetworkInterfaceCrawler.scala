package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeNetworkInterfacesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record

import scala.collection.JavaConverters._

/** crawler for Network Interfaces
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsNetworkInterfaceCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeNetworkInterfacesRequest

  override def doCrawl()(implicit req: RequestId) = {
    val list = backoffRequest {
      ctx.awsClient.ec2.describeNetworkInterfaces(request).getNetworkInterfaces
    }.asScala.map(
      item => {
        Record(item.getNetworkInterfaceId, ctx.beanMapper(item))
      }
    )
    list
  }
}
