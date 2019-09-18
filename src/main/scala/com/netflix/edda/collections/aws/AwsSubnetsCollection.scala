package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsSubnetCrawler
import com.netflix.edda.electors.Elector

class AwsSubnetsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.subnets", accountName, ctx) {

  val crawler = new AwsSubnetCrawler(name, ctx)
}
