package com.netflix.edda.crawlers.aws

import com.amazonaws.services.route53.model.ListHostedZonesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record

import scala.collection.JavaConverters._

/** crawler for Route53 Hosted Zones (DNS records)
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsHostedZoneCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListHostedZonesRequest

  override def doCrawl()(implicit req: RequestId) =
    backoffRequest { ctx.awsClient.r53.listHostedZones(request).getHostedZones }.asScala
      .map(item => Record(item.getName, ctx.beanMapper(item)))
      .toSeq
}
