package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeVpcsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for ASG VPCs
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsVpcCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribeVpcsRequest

  override def doCrawl()(implicit req: RequestId) = {
    val response = backoffRequest { ctx.awsClient.ec2.describeVpcs() }
    response.getVpcs.asScala
      .map(item => {
        Record(item.getVpcId, ctx.beanMapper(item))
      })
      .toList
  }
}
