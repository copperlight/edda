package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsLaunchConfigurationCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS LaunchConfigurations
  *
  * root collection name: aws.launchConfigurations
  *
  * see crawler details [[AwsLaunchConfigurationCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsLaunchConfigurationCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.launchConfigurations", accountName, ctx) {
  val crawler = new AwsLaunchConfigurationCrawler(name, ctx)
}
