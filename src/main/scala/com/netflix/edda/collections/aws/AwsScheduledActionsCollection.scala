package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsScheduledActionsCrawler
import com.netflix.edda.electors.Elector

class AwsScheduledActionsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.scheduledActions", accountName, ctx) {

  val crawler = new AwsScheduledActionsCrawler(name, ctx)
}
