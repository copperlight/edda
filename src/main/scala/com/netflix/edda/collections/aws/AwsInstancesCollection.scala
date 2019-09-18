package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsReservationCrawler
import com.netflix.edda.electors.Elector

class AwsInstancesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.instances", accountName, ctx) {

  val crawler = new AwsReservationCrawler(name, ctx)
}
