package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsIamVirtualMFADeviceCrawler
import com.netflix.edda.electors.Elector

/** collection for AWS IAM VirtualMFADevices
  *
  * root collection name: aws.iamVirtualMFADevices
  *
  * see crawler details [[AwsIamVirtualMFADeviceCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for configuration and AWS clients objects
  */
class AwsIamVirtualMFADeviceCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.iamVirtualMFADevices", accountName, ctx) {
  val crawler = new AwsIamVirtualMFADeviceCrawler(name, ctx)
}
