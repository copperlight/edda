package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsTagCrawler
import com.netflix.edda.electors.Elector

class AwsTagsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.tags", accountName, ctx) {

  val crawler = new AwsTagCrawler(name, ctx)
}
