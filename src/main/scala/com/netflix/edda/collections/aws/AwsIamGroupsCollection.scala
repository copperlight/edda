package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsIamGroupCrawler
import com.netflix.edda.electors.Elector

class AwsIamGroupsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamGroups", accountName, ctx) {

  val crawler = new AwsIamGroupCrawler(name, ctx)
}
