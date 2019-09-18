package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsReservedInstancesOfferingCrawler
import com.netflix.edda.electors.Elector

class AwsReservedInstancesOfferingsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.reservedInstancesOfferings", accountName, ctx) {

  val crawler = new AwsReservedInstancesOfferingCrawler(name, ctx)
}
