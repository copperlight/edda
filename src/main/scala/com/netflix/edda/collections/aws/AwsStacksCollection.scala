package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsStacksCrawler
import com.netflix.edda.electors.Elector

class AwsStacksCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.stacks", accountName, ctx) {

  val crawler = new AwsStacksCrawler(name, ctx)
}
