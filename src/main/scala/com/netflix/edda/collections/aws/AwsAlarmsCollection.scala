package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsAlarmCrawler
import com.netflix.edda.electors.Elector

class AwsAlarmsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.alarms", accountName, ctx) {

  val crawler = new AwsAlarmCrawler(name, ctx)
}
