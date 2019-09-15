package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeTagsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record

import scala.collection.JavaConverters._

/** crawler for all Tags
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsTagCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeTagsRequest

  override def doCrawl()(implicit req: RequestId) =
    backoffRequest { ctx.awsClient.ec2.describeTags(request).getTags }.asScala
      .map(
        item =>
          Record(
            item.getKey + "|" + item.getResourceType + "|" + item.getResourceId,
            ctx.beanMapper(item)
          )
      )
      .toSeq
}
