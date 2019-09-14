package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsScheduledActionsCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS AutoScaling Scheduled Actions
  *
  * root collection name: aws.scheduledActions
  *
  * see crawler details [[AwsScheduledActionsCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsScheduledActionsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.scheduledActions", accountName, ctx) {
  val crawler = new AwsScheduledActionsCrawler(name, ctx)
}
