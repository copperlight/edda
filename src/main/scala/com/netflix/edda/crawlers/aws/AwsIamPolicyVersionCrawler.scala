package com.netflix.edda.crawlers.aws

import com.amazonaws.services.identitymanagement.model.ListPolicyVersionsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/** crawler for IAM Policy Versions
  *
  */
class AwsIamPolicyVersionCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListPolicyVersionsRequest

  override def doCrawl()(implicit req: RequestId) =
    ctx.awsClient.idm
      .listPolicyVersions(request)
      .getVersions
      .asScala
      .map(
        item => Record(item.getVersionId, new DateTime(item.getCreateDate), ctx.beanMapper(item))
      )
      .toSeq
}
