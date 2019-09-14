package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsReservedInstancesOfferingCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Reserved Instance Offerings
  *
  * root collection name: aws.vpcs
  *
  * see crawler details [[AwsReservedInstancesOfferingCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsReservedInstancesOfferingCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.reservedInstancesOfferings", accountName, ctx) {
  val crawler = new AwsReservedInstancesOfferingCrawler(name, ctx)
}
