package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsImageCrawler
import com.netflix.edda.electors.Elector

class AwsImagesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.images", accountName, ctx) {

  val crawler = new AwsImageCrawler(name, ctx)
}
