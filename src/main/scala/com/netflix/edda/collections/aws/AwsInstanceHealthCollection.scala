package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsInstanceHealthCrawler
import com.netflix.edda.crawlers.AwsLoadBalancerCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Elastic Load Balancer Instances
  *
  * root collection name: view.loadBalancerInstances
  *
  * see crawler details [[AwsInstanceHealthCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsInstanceHealthCollection(
  val elbCrawler: AwsLoadBalancerCrawler,
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("view.loadBalancerInstances", accountName, ctx) {
  // we dont actually crawl, the elbCrawler triggers our crawl events
  override val allowCrawl = false
  val crawler = new AwsInstanceHealthCrawler(name, ctx, elbCrawler)
}
