package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsIamVirtualMFADeviceCrawler
import com.netflix.edda.electors.Elector

class AwsIamVirtualMFADevicesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamVirtualMFADevices", accountName, ctx) {

  val crawler = new AwsIamVirtualMFADeviceCrawler(name, ctx)
}
