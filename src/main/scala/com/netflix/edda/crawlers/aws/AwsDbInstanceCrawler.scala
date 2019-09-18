package com.netflix.edda.crawlers.aws

import com.amazonaws.services.rds.model.DescribeDBInstancesRequest
import com.amazonaws.services.rds.model.ListTagsForResourceRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/** crawler for RDS Databases
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsDbInstanceCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribeDBInstancesRequest
  request.setMaxRecords(50)

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator() {
      def next() = {
        // annoying, describeDBInstances has withMarker and getMarker instead if withToken and getNextToken
        val response = backoffRequest {
          ctx.awsClient.rds.describeDBInstances(request.withMarker(this.nextToken.get))
        }
        this.nextToken = Option(response.getMarker)
        response.getDBInstances.asScala
          .map(item => {
            Record(item.getDBInstanceIdentifier, ctx.beanMapper(item))
          })
          .toList
      }
    }

    backoffRequest { ctx.awsClient.loadAccountNum() }

    val initial = it.toList.flatten
    var buffer = new ListBuffer[Record]()
    for (rec <- initial) {
      val data = rec.toMap("data").asInstanceOf[Map[String, String]]
      val arn = ctx.awsClient.arn("rds", "db", data("DBInstanceIdentifier"))

      val request = new ListTagsForResourceRequest().withResourceName(arn)
      val response = backoffRequest { ctx.awsClient.rds.listTagsForResource(request) }
      val responseList = response.getTagList.asScala
        .map(item => {
          ctx.beanMapper(item)
        })
        .toList

      buffer += rec.copy(
        data = data.asInstanceOf[Map[String, Any]] ++ Map("arn" -> arn, "tags" -> responseList)
      )
    }
    buffer.toList
  }
}
