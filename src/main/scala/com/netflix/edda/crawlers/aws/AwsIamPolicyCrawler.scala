package com.netflix.edda.crawlers.aws

import com.amazonaws.services.identitymanagement.model.GetPolicyVersionRequest
import com.amazonaws.services.identitymanagement.model.ListPoliciesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/** crawler for IAM policies
  *
  * @param name name of context we are crawling for
  * @param ctx context to provide beanMapper and configuration
  */
class AwsIamPolicyCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListPoliciesRequest
  val vr = new GetPolicyVersionRequest

  override def doCrawl()(implicit req: RequestId) = {
    ctx.awsClient.idm
      .listPolicies(request)
      .getPolicies
      .asScala
      .map(item => {
        vr.setPolicyArn(item.getArn)
        vr.setVersionId(item.getDefaultVersionId)
        val version = ctx.awsClient.idm.getPolicyVersion(vr).getPolicyVersion

        val data = ctx.beanMapper(item).asInstanceOf[Map[String, Any]] ++ Map(
          "defaultDocument" -> version.getDocument
        )

        Record(item.getPolicyName, new DateTime(item.getUpdateDate), data)
      })
      .toSeq
  }
}
