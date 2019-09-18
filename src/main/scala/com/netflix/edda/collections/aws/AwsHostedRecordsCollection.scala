package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsHostedRecordCrawler
import com.netflix.edda.crawlers.aws.AwsHostedZoneCrawler
import com.netflix.edda.electors.Elector

class AwsHostedRecordsCollection(
  val zoneCrawler: AwsHostedZoneCrawler,
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.hostedRecords", accountName, ctx) {

  // we dont actually crawl, the zoneCrawler triggers our crawl events
  override val allowCrawl = false
  val crawler = new AwsHostedRecordCrawler(name, ctx, zoneCrawler)
}
