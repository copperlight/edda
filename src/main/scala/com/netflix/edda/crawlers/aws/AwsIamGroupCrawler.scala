package com.netflix.edda.crawlers.aws

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import com.amazonaws.services.identitymanagement.model.ListGroupPoliciesRequest
import com.amazonaws.services.identitymanagement.model.ListGroupsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for IAM Groups
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper and configuration
  */
class AwsIamGroupCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListGroupsRequest
  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val threadPool = Executors.newFixedThreadPool(10)

  override def doCrawl()(implicit req: RequestId) = {
    val groups = backoffRequest {
      ctx.awsClient.idm.listGroups(request).getGroups.asScala
    }
    val futures: Seq[java.util.concurrent.Future[Record]] = groups.map(
      group => {
        threadPool.submit(
          new Callable[Record] {
            def call() = {
              val groupPoliciesRequest =
                new ListGroupPoliciesRequest().withGroupName(group.getGroupName)
              val groupPolicies = backoffRequest {
                ctx.awsClient.idm
                  .listGroupPolicies(groupPoliciesRequest)
                  .getPolicyNames
                  .asScala
                  .toSeq
              }
              Record(
                group.getGroupName,
                new DateTime(group.getCreateDate),
                Map(
                  "name"       -> group.getGroupName,
                  "attributes" -> (ctx.beanMapper(group)),
                  "policies"   -> groupPolicies
                )
              )
            }
          }
        )
      }
    )
    var failed = false
    val records = futures
      .map(
        f => {
          try Some(f.get)
          catch {
            case e: Exception => {
              if (logger.isErrorEnabled)
                logger.error(s"$req$this exception from IAM listGroupPolicies", e)
              failed = true
              None
            }
          }
        }
      )
      .collect {
        case Some(rec) => rec
      }

    if (failed) {
      throw new java.lang.RuntimeException(s"$this failed to crawl resource record sets")
    }
    records
  }

}
