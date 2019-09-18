package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsNetworkInterfaceCrawler
import com.netflix.edda.electors.Elector

class AwsNetworkInterfacesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.networkInterfaces", accountName, ctx) {
  val crawler = new AwsNetworkInterfaceCrawler(name, ctx)
}
