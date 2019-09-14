package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsIamUserCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS IAM Users
  *
  * root collection name: aws.iamUsers
  *
  * see crawler details [[AwsIamUserCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for configuration and AWS clients objects
  */
class AwsIamUserCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamUsers", accountName, ctx) {
  val crawler = new AwsIamUserCrawler(name, ctx)
}
