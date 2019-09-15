package com.netflix.edda.crawlers.aws

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest
import com.amazonaws.services.identitymanagement.model.ListGroupsForUserRequest
import com.amazonaws.services.identitymanagement.model.ListUserPoliciesRequest
import com.amazonaws.services.identitymanagement.model.ListUsersRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for IAM Users
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper and configuration
  */
class AwsIamUserCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListUsersRequest
  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val threadPool = Executors.newFixedThreadPool(10)

  override def doCrawl()(implicit req: RequestId) = {
    val users = backoffRequest {
      ctx.awsClient.idm.listUsers(request).getUsers.asScala
    }
    val futures: Seq[java.util.concurrent.Future[Record]] = users.map(
      user => {
        threadPool.submit(
          new Callable[Record] {
            def call() = {
              val groupsRequest = new ListGroupsForUserRequest().withUserName(user.getUserName)
              val groups = backoffRequest {
                ctx.awsClient.idm.listGroupsForUser(groupsRequest).getGroups
              }.asScala.map(item => item.getGroupName).toSeq
              val accessKeysRequest = new ListAccessKeysRequest().withUserName(user.getUserName)
              val accessKeys = Map[String, String]() ++ backoffRequest {
                ctx.awsClient.idm
                  .listAccessKeys(accessKeysRequest)
                  .getAccessKeyMetadata
              }.asScala.map(item => ctx.beanMapper(item)).toSeq
              val userPoliciesRequest = new ListUserPoliciesRequest().withUserName(user.getUserName)
              val userPolicies = backoffRequest {
                ctx.awsClient.idm
                  .listUserPolicies(userPoliciesRequest)
                  .getPolicyNames
                  .asScala
              }
              Record(
                user.getUserName,
                new DateTime(user.getCreateDate),
                Map(
                  "name"         -> user.getUserName,
                  "attributes"   -> (ctx.beanMapper(user)),
                  "groups"       -> groups,
                  "accessKeys"   -> accessKeys,
                  "userPolicies" -> userPolicies
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
                logger.error(s"$req$this exception from IAM user sub requests", e)
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
