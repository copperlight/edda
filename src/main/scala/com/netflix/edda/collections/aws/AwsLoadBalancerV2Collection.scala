package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsLoadBalancerV2Crawler
import com.netflix.edda.electors.Elector

/** collection for AWS Elastic Load Balancers (version 2)
  *
  * root collection name: aws.loadBalancersV2
  *
  * see crawler details [[AwsLoadBalancerV2Crawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsLoadBalancerV2Collection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.loadBalancersV2", accountName, ctx) {
  val crawler = new AwsLoadBalancerV2Crawler(name, ctx)
}
