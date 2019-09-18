package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsScalingActivitiesCrawler
import com.netflix.edda.electors.Elector

class AwsScalingActivitiesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.scalingActivities", accountName, ctx) {

  val crawler = new AwsScalingActivitiesCrawler(name, ctx)
}
