package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsLoadBalancerV2Crawler
import com.netflix.edda.electors.Elector

class AwsLoadBalancersV2Collection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.loadBalancersV2", accountName, ctx) {
  val crawler = new AwsLoadBalancerV2Crawler(name, ctx)
}
