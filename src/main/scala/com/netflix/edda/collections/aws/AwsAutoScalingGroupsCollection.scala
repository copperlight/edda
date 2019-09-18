package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsAutoScalingGroupCrawler
import com.netflix.edda.electors.Elector

class AwsAutoScalingGroupsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.autoScalingGroups", accountName, ctx) {

  val crawler = new AwsAutoScalingGroupCrawler(name, ctx)
}
