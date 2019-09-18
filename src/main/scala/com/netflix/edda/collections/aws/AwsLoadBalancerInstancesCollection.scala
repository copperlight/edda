package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsInstanceHealthCrawler
import com.netflix.edda.crawlers.aws.AwsLoadBalancerCrawler
import com.netflix.edda.electors.Elector

class AwsLoadBalancerInstancesCollection(
  val elbCrawler: AwsLoadBalancerCrawler,
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("view.loadBalancerInstances", accountName, ctx) {

  // we dont actually crawl, the elbCrawler triggers our crawl events
  override val allowCrawl = false
  val crawler = new AwsInstanceHealthCrawler(name, ctx, elbCrawler)
}
