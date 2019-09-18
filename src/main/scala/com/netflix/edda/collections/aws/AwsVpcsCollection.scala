package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsVpcCrawler
import com.netflix.edda.electors.Elector

class AwsVpcsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.vpcs", accountName, ctx) {

  val crawler = new AwsVpcCrawler(name, ctx)
}
