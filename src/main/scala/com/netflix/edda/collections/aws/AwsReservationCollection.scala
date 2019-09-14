package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsReservationCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Reservations of EC2 Instances
  *
  * root collection name: aws.instances
  *
  * see crawler details [[AwsReservationCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsReservationCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.instances", accountName, ctx) {
  val crawler = new AwsReservationCrawler(name, ctx)
}
