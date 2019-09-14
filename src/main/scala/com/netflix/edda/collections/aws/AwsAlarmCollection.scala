package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsAlarmCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS ASG Alarms
  *
  * root collection name: aws.alarms
  *
  * see crawler details [[AwsAlarmCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsAlarmCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.alarms", accountName, ctx) {
  val crawler = new AwsAlarmCrawler(name, ctx)
}
