package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsReservedInstanceCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Reserved Instances (pre-paid instances)
  *
  * root collection name: aws.reservedInstances
  *
  * see crawler details [[AwsReservedInstanceCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsReservedInstanceCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.reservedInstances", accountName, ctx) {
  val crawler = new AwsReservedInstanceCrawler(name, ctx)
}
