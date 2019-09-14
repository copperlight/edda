package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsSecurityGroupCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Security Groups
  *
  * root collection name: aws.securityGroups
  *
  * see crawler details [[AwsSecurityGroupCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsSecurityGroupCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.securityGroups", accountName, ctx) {
  val crawler = new AwsSecurityGroupCrawler(name, ctx)
}
