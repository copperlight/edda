package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsAutoScalingGroupCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS AutoScalingGroups
  *
  * root collection name: aws.autoScalingGroups
  *
  * see crawler details [[AwsAutoScalingGroupCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsAutoScalingGroupsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.autoScalingGroups", accountName, ctx) {
  val crawler = new AwsAutoScalingGroupCrawler(name, ctx)
}
