package com.netflix.edda.crawlers.aws

import com.amazonaws.services.s3.model.ListBucketsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/** crawler for S3 Buckets
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsBucketCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListBucketsRequest

  override def doCrawl()(implicit req: RequestId) =
    backoffRequest { ctx.awsClient.s3.listBuckets(request) }.asScala
      .map(item => Record(item.getName, new DateTime(item.getCreationDate), ctx.beanMapper(item)))
      .toSeq
}
