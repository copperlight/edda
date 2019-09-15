package com.netflix.edda.crawlers.aws

import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import com.netflix.edda.util.Common

import scala.collection.JavaConverters._

/** crawler for Images
  *
  * if tags are used with on the aws images objects, set edda.crawler.NAME.abortWithoutTags=true
  * so that the crawler can detect when AWS sends a degraded result without tag information.
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsImageCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeImagesRequest
  lazy val abortWithoutTags = Common.getProperty("edda.crawler", "abortWithoutTags", name, "false")

  override def doCrawl()(implicit req: RequestId) = {
    var tagCount = 0
    val list = backoffRequest { ctx.awsClient.ec2.describeImages(request).getImages }.asScala
      .map(item => {
        tagCount += item.getTags.size
        Record(item.getImageId, ctx.beanMapper(item))
      })
      .toSeq
    if (tagCount == 0 && abortWithoutTags.get.toBoolean) {
      throw new java.lang.RuntimeException("no tags found for " + name + ", ignoring crawl results")
    }
    list
  }
}
