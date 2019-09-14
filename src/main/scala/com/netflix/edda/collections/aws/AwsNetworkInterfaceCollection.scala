package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsNetworkInterfaceCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Network Interfaces
  *
  * root collection name: aws.networkInterfaces
  *
  * see crawler details [[AwsNetworkInterfaceCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsNetworkInterfaceCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.networkInterfaces", accountName, ctx) {
  val crawler = new AwsNetworkInterfaceCrawler(name, ctx)
}
