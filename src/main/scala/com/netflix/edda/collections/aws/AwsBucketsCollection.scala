package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsBucketCrawler
import com.netflix.edda.electors.Elector

class AwsBucketsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.buckets", accountName, ctx) {

  val crawler = new AwsBucketCrawler(name, ctx)
}
