package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsAddressCrawler
import com.netflix.edda.electors.Elector

class AwsAddressesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.addresses", accountName, ctx) {

  val crawler = new AwsAddressCrawler(name, ctx)
}
