package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsCacheClusterCrawler
import com.netflix.edda.electors.Elector

class AwsCacheClustersCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.cacheClusters", accountName, ctx) {

  val crawler = new AwsCacheClusterCrawler(name, ctx)
}
