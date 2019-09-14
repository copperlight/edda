package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsIamRoleCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS IAM Roles
  *
  * root collection name: aws.iamRoles
  *
  * see crawler details [[AwsIamRoleCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for configuration and AWS clients objects
  */
class AwsIamRoleCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamRoles", accountName, ctx) {
  val crawler = new AwsIamRoleCrawler(name, ctx)
}
