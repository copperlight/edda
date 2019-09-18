package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsSnapshotCrawler
import com.netflix.edda.electors.Elector

class AwsSnapshotsCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.snapshots", accountName, ctx) {

  val crawler = new AwsSnapshotCrawler(name, ctx)
}
