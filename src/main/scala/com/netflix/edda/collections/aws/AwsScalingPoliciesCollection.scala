package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsScalingPolicyCrawler
import com.netflix.edda.electors.Elector

class AwsScalingPoliciesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.scalingPolicies", accountName, ctx) {

  val crawler = new AwsScalingPolicyCrawler(name, ctx)
}
