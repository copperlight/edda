package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsCacheClusterCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS ElastiCache clusters
  *
  * root collection name: aws.cacheClusters
  *
  * see crawler details [[AwsCacheClusterCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsCacheClusterCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.cacheClusters", accountName, ctx) {
  val crawler = new AwsCacheClusterCrawler(name, ctx)
}
