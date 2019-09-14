package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsAddressCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Addresses
  *
  * root collection name: aws.addresses
  *
  * see crawler details [[AwsAddressCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsAddressCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.addresses", accountName, ctx) {
  val crawler = new AwsAddressCrawler(name, ctx)
}
