package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsTargetGroupCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Target Groups
  *
  * root collection name: aws.targetGroups
  *
  * see crawler details [[AwsTargetGroupCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsTargetGroupCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.targetGroups", accountName, ctx) {
  val crawler = new AwsTargetGroupCrawler(name, ctx)
}
