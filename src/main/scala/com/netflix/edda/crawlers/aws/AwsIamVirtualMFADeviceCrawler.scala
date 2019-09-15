package com.netflix.edda.crawlers.aws

import com.amazonaws.services.identitymanagement.model.ListVirtualMFADevicesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/** crawler for IAM Virtual MFA Devices
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper and configuration
  */
class AwsIamVirtualMFADeviceCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListVirtualMFADevicesRequest

  override def doCrawl()(implicit req: RequestId) =
    backoffRequest {
      ctx.awsClient.idm.listVirtualMFADevices(request).getVirtualMFADevices
    }.asScala
      .map(
        item =>
          Record(
            item.getSerialNumber.split('/').last,
            new DateTime(item.getEnableDate),
            ctx.beanMapper(item)
          )
      )
      .toSeq
}
