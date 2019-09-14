package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsSnapshotCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS Snapshots
  *
  * root collection name: aws.snapshots
  *
  * see crawler details [[AwsSnapshotCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsSnapshotCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.snapshots", accountName, ctx) {
  val crawler = new AwsSnapshotCrawler(name, ctx)
}
