package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsIamPolicyCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS IAM Policies
  *
  * root collection name: aws.iamPolicies
  *
  * see crawler details [[AwsIamPolicyCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for configuration and AWS clients objects
  */
class AwsIamPolicyCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamPolicies", accountName, ctx) {
  val crawler = new AwsIamPolicyCrawler(name, ctx)
}
