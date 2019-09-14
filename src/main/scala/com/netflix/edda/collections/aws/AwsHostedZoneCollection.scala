package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsHostedZoneCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Route53 hosted zones
  *
  * root collection name: aws.hostedZones
  *
  * see crawler details [[AwsHostedZoneCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsHostedZoneCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.hostedZones", accountName, ctx) {
  val crawler = new AwsHostedZoneCrawler(name, ctx)
}
