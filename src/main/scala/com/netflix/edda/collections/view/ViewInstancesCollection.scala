package com.netflix.edda.collections.view

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsInstanceCrawler
import com.netflix.edda.crawlers.aws.AwsReservationCrawler
import com.netflix.edda.electors.Elector

class ViewInstancesCollection(
  val resCrawler: AwsReservationCrawler,
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("view.instances", accountName, ctx) {

  // we dont actually crawl, the resCrawler triggers our crawl events
  override val allowCrawl = false
  val crawler = new AwsInstanceCrawler(name, ctx, resCrawler)
}
