package com.netflix.edda.crawlers.aws

import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record

import scala.collection.JavaConverters._

/** crawler for Subnets
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsDbSubnetCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  override def doCrawl()(implicit req: RequestId) =
    ctx.awsClient.rds
      .describeDBSubnetGroups()
      .getDBSubnetGroups
      .asScala
      .map(item => {
        Record(item.getDBSubnetGroupName, ctx.beanMapper(item))
      })
}
