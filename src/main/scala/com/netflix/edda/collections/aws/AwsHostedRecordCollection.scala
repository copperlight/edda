package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsHostedRecordCrawler
import com.netflix.edda.crawlers.AwsHostedZoneCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Route53 record sets
  *
  * root collection name: aws.hostedRecords
  *
  * see crawler details [[AwsHostedRecordCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsHostedRecordCollection(
  val zoneCrawler: AwsHostedZoneCrawler,
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.hostedRecords", accountName, ctx) {
  // we dont actually crawl, the zoneCrawler triggers our crawl events
  override val allowCrawl = false
  val crawler = new AwsHostedRecordCrawler(name, ctx, zoneCrawler)
}
