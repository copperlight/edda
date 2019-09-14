package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsScalingActivitiesCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS AutoScaling Activities
  *
  * root collection name: aws.scalingActivities
  *
  * see crawler details [[AwsScalingActivitiesCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsScalingActivitiesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.scalingActivities", accountName, ctx) {
  val crawler = new AwsScalingActivitiesCrawler(name, ctx)
}
