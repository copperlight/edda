package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsHostedZoneCrawler
import com.netflix.edda.electors.Elector

class AwsHostedZonesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.hostedZones", accountName, ctx) {

  val crawler = new AwsHostedZoneCrawler(name, ctx)
}
