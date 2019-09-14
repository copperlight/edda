package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsBucketCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS S3 Buckets
  *
  * root collection name: aws.buckets
  *
  * see crawler details [[AwsBucketCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsBucketCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.buckets", accountName, ctx) {
  val crawler = new AwsBucketCrawler(name, ctx)
}
