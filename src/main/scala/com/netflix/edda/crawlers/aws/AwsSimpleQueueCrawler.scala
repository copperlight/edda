package com.netflix.edda.crawlers.aws

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.ListQueuesRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/** crawler for SQS Queues
  *
  * This crawler is similar to the InstanceHealth crawler in that it has to first
  * get a list of SQS Queues then for each queue fan-out and query each queue.
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsSimpleQueueCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new ListQueuesRequest
  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val threadPool = Executors.newFixedThreadPool(10)

  override def doCrawl()(implicit req: RequestId) = {
    val queues = backoffRequest { ctx.awsClient.sqs.listQueues(request).getQueueUrls.asScala }
    val futures: Seq[java.util.concurrent.Future[Record]] = queues.map(
      queueUrl => {
        threadPool.submit(
          new Callable[Record] {
            def call() = {
              val name = queueUrl.split('/').last
              val attrRequest =
                new GetQueueAttributesRequest().withQueueUrl(queueUrl).withAttributeNames("All")
              val attrs = Map[String, String]() ++ backoffRequest {
                ctx.awsClient.sqs.getQueueAttributes(attrRequest).getAttributes.asScala
              }
              val ctime = attrs.get("CreatedTimestamp") match {
                case Some(time) => new DateTime(time.toInt * 1000)
                case None       => DateTime.now
              }

              Record(name, ctime, Map("name" -> name, "url" -> queueUrl, "attributes" -> (attrs)))
            }
          }
        )
      }
    )
    var failed: Boolean = false
    val records = futures
      .map(
        f => {
          try Some(f.get)
          catch {
            case e: java.util.concurrent.ExecutionException => {
              e.getCause match {
                case e: AmazonServiceException
                  if e.getErrorCode == "AWS.SimpleQueueService.NonExistentQueue" => {
                  // this happens constantly, dont log it.  There is a large time delta between queues being deleted
                  // but still showing up in the ListQueuesResult
                  None
                }
                case e: Throwable => {
                  if (logger.isErrorEnabled)
                    logger.error(s"$req$this exception from SQS getQueueAttributes", e)
                  failed = true
                  None
                }
              }
            }
            case e: Throwable => {
              if (logger.isErrorEnabled)
                logger.error(s"$req$this exception from SQS getQueueAttributes", e)
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
