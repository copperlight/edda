package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsIamUserCrawler
import com.netflix.edda.electors.Elector

class AwsIamUsersCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamUsers", accountName, ctx) {

  val crawler = new AwsIamUserCrawler(name, ctx)
}
