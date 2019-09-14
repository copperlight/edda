package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsCloudformationCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Cloudformation Stacks
  *
  * root collection name: aws.stacks
  *
  * see crawler details [[AwsCloudformationCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsCloudformationCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.stacks", accountName, ctx) {
  val crawler = new AwsCloudformationCrawler(name, ctx)
}
