package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsVpcCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS VPCs
  *
  * root collection name: aws.vpcs
  *
  * see crawler details [[AwsVpcCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsVpcCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.vpcs", accountName, ctx) {
  val crawler = new AwsVpcCrawler(name, ctx)
}
