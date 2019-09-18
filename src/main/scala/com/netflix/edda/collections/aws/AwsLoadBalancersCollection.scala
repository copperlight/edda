package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsLoadBalancerCrawler
import com.netflix.edda.electors.Elector

class AwsLoadBalancersCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.loadBalancers", accountName, ctx) {

  val crawler = new AwsLoadBalancerCrawler(name, ctx)
}
