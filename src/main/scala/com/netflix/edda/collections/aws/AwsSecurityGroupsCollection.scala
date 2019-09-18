package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsSecurityGroupCrawler
import com.netflix.edda.electors.Elector

class AwsSecurityGroupsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.securityGroups", accountName, ctx) {

  val crawler = new AwsSecurityGroupCrawler(name, ctx)
}
