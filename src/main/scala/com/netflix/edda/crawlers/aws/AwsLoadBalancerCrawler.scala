package com.netflix.edda.crawlers.aws

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/** crawler for LoadBalancers
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsLoadBalancerCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribeLoadBalancersRequest
  request.setPageSize(400)

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator() {
      def next() = {
        val response = backoffRequest {
          ctx.awsClient.elb.describeLoadBalancers(request.withMarker(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextMarker)
        response.getLoadBalancerDescriptions.asScala
          .map(item => {
            Record(
              item.getLoadBalancerName,
              new DateTime(item.getCreatedTime),
              ctx.beanMapper(item)
            )
          })
          .toList
      }
    }

    // List[Seq[Record]]
    var initial = it.toSeq.flatten.grouped(20).toList
    backoffRequest { ctx.awsClient.loadAccountNum() }

    var buffer = new ListBuffer[Record]()

    for (group <- initial) {
      var names = new ListBuffer[String]()

      for (rec <- group) {
        val data = rec.toMap("data").asInstanceOf[Map[String, String]]
        names += data("loadBalancerName")
      }
      try {
        val request = new com.amazonaws.services.elasticloadbalancing.model.DescribeTagsRequest()
          .withLoadBalancerNames(names.asJava)
        val response = backoffRequest { ctx.awsClient.elb.describeTags(request) }
        val responseList = backoffRequest {
          response
            .getTagDescriptions()
            .asScala
            .map(item => {
              ctx.beanMapper(item)
            })
            .toSeq
        }

        for (rec <- group) {
          val data = rec.toMap("data").asInstanceOf[Map[String, String]]
          for (response <- responseList) {
            if (response.asInstanceOf[Map[String, Any]]("loadBalancerName") == data(
              "loadBalancerName"
            )) {
              buffer += rec.copy(
                data = data.asInstanceOf[Map[String, Any]] ++ Map(
                  "tags" -> response.asInstanceOf[Map[String, Any]]("tags")
                )
              )
            }
          }
        }
      } catch {
        case e: Exception => {
          logger.error("error retrieving tags for an elb", e)
        }
      }
    }
    buffer.toList
  }
}
