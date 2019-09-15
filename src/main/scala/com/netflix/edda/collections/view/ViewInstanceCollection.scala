package com.netflix.edda.collections.view

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsReservationCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Instances
  *
  * root collection name: view.instances
  *
  * see crawler details [[AwsInstanceCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class ViewInstanceCollection(
  val resCrawler: AwsReservationCrawler,
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("view.instances", accountName, ctx) {
  // we dont actually crawl, the resCrawler triggers our crawl events
  override val allowCrawl = false
  val crawler = new AwsInstanceCrawler(name, ctx, resCrawler)
}
