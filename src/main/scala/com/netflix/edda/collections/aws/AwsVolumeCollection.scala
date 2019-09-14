package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsVolumeCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Volumes
  *
  * root collection name: aws.volumes
  *
  * see crawler details [[AwsVolumeCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsVolumeCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.volumes", accountName, ctx) {
  val crawler = new AwsVolumeCrawler(name, ctx)
}
