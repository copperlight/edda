package com.netflix.edda.crawlers.aws

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.model.ListStackResourcesRequest
import com.amazonaws.services.cloudformation.model.Stack
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for Cloudformation Stacks
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsCloudformationCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val threadPool = Executors.newFixedThreadPool(1)

  private def getStacksFromAws: List[Stack] = {
    val stacks = List.newBuilder[Stack]
    val request = new DescribeStacksRequest
    var token: String = null
    do {
      val response = backoffRequest {
        ctx.awsClient.cloudformation.describeStacks(request.withNextToken(token))
      }
      stacks ++= response.getStacks.asScala
      token = response.getNextToken
    } while (token != null)
    stacks.result
  }

  override def doCrawl()(implicit req: RequestId) = {
    val stacks = getStacksFromAws
    val futures: Seq[java.util.concurrent.Future[Record]] = stacks.map(
      stack => {
        this.threadPool.submit(
          new Callable[Record] {
            def call() = {
              val stackResourcesRequest =
                new ListStackResourcesRequest().withStackName(stack.getStackName)
              val stackResources = backoffRequest {
                ctx.awsClient.cloudformation
                  .listStackResources(stackResourcesRequest)
                  .getStackResourceSummaries
                  .asScala
                  .map(item => ctx.beanMapper(item))
              }
              Record(
                stack.getStackName,
                new DateTime(stack.getCreationTime),
                ctx.beanMapper(stack).asInstanceOf[Map[String, Any]] ++ Map(
                  "resources" -> stackResources
                )
              )
            }
          }
        )
      }
    )
    val records = futures
      .map(
        f => {
          try Some(f.get)
          catch {
            case e: Exception => {
              if (logger.isErrorEnabled)
                logger.error(this + "exception from Cloudformation listStackResources", e)
              None
            }
          }
        }
      )
      .collect {
        case Some(rec) => rec
      }
    records
  }

}
