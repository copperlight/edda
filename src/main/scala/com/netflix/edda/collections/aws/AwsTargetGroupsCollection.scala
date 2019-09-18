package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsTargetGroupCrawler
import com.netflix.edda.electors.Elector

class AwsTargetGroupsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.targetGroups", accountName, ctx) {

  val crawler = new AwsTargetGroupCrawler(name, ctx)
}
