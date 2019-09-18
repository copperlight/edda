package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsVolumeCrawler
import com.netflix.edda.electors.Elector

class AwsVolumesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.volumes", accountName, ctx) {

  val crawler = new AwsVolumeCrawler(name, ctx)
}
