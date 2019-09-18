package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsLaunchConfigurationCrawler
import com.netflix.edda.electors.Elector

class AwsLaunchConfigurationsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.launchConfigurations", accountName, ctx) {

  val crawler = new AwsLaunchConfigurationCrawler(name, ctx)
}
