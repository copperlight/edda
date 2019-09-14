package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsScalingPolicyCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS ASG Scaling Policies
  *
  * root collection name: aws.scalingPolicies
  *
  * see crawler details [[AwsScalingPolicyCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsScalingPolicyCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.scalingPolicies", accountName, ctx) {
  val crawler = new AwsScalingPolicyCrawler(name, ctx)
}
