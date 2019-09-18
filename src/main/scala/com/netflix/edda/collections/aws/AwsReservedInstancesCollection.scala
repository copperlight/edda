package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsReservedInstanceCrawler
import com.netflix.edda.electors.Elector

class AwsReservedInstancesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.reservedInstances", accountName, ctx) {

  val crawler = new AwsReservedInstanceCrawler(name, ctx)
}
