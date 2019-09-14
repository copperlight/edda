package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsImageCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Images
  *
  * root collection name: aws.images
  *
  * see crawler details [[AwsImageCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsImageCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.images", accountName, ctx) {
  val crawler = new AwsImageCrawler(name, ctx)
}
