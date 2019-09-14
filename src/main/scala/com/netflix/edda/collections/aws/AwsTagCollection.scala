package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsTagCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Tags
  *
  * root collection name: aws.tags
  *
  * see crawler details [[AwsTagCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsTagCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.tags", accountName, ctx) {
  val crawler = new AwsTagCrawler(name, ctx)
}
