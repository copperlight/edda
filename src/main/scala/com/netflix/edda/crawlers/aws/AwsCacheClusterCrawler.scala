package com.netflix.edda.crawlers.aws

import com.amazonaws.services.elasticache.model.DescribeCacheClustersRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record

import scala.collection.JavaConverters._

/** crawler for ElastiCache Clusters
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsCacheClusterCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeCacheClustersRequest

  override def doCrawl()(implicit req: RequestId) =
    backoffRequest { ctx.awsClient.elastiCache.describeCacheClusters(request).getCacheClusters }.asScala
      .map(item => Record(item.getCacheClusterId, ctx.beanMapper(item)))
      .toSeq
}
