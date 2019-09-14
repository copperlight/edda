package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsLoadBalancerCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Elastic Load Balancers
  *
  * root collection name: aws.loadBalancers
  *
  * see crawler details [[AwsLoadBalancerCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsLoadBalancerCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.loadBalancers", accountName, ctx) {
  val crawler = new AwsLoadBalancerCrawler(name, ctx)
}
