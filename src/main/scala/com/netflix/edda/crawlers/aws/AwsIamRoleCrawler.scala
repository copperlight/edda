package com.netflix.edda.crawlers.aws

import com.amazonaws.services.identitymanagement.model.ListRolesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/** crawler for IAM Roles
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper and configuration
  */
class AwsIamRoleCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListRolesRequest

  override def doCrawl()(implicit req: RequestId) =
    backoffRequest { ctx.awsClient.idm.listRoles(request).getRoles }.asScala
      .map(item => Record(item.getRoleName, new DateTime(item.getCreateDate), ctx.beanMapper(item)))
      .toSeq
}
