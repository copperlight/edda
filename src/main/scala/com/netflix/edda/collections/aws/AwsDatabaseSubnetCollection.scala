package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsDatabaseSubnetCrawler
import com.netflix.edda.electors.Elector

class AwsDatabaseSubnetCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.databaseSubnets", accountName, ctx) {
  val crawler = new AwsDatabaseSubnetCrawler(name, ctx)
}
