package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsIamPolicyCrawler
import com.netflix.edda.electors.Elector

class AwsIamPoliciesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamPolicies", accountName, ctx) {

  val crawler = new AwsIamPolicyCrawler(name, ctx)
}
