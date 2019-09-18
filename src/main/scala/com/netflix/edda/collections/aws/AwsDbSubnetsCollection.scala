package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsDbSubnetCrawler
import com.netflix.edda.electors.Elector

class AwsDbSubnetsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.dbSubnets", accountName, ctx) {

  val crawler = new AwsDbSubnetCrawler(name, ctx)
}
