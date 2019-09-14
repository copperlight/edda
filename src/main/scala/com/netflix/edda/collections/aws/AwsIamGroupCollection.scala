package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsIamGroupCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS IAM Groups
  *
  * root collection name: aws.iamGroups
  *
  * see crawler details [[AwsIamGroupCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for configuration and AWS clients objects
  */
class AwsIamGroupCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamGroups", accountName, ctx) {
  val crawler = new AwsIamGroupCrawler(name, ctx)
}
