package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsIamRoleCrawler
import com.netflix.edda.electors.Elector

class AwsIamRolesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamRoles", accountName, ctx) {

  val crawler = new AwsIamRoleCrawler(name, ctx)
}
