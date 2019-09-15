package com.netflix.edda.crawlers.aws

import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.{DescribeLoadBalancersRequest => DescribeLoadBalancersV2Request}
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
/** crawler for LoadBalancers (version 2)
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsLoadBalancerV2Crawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  val request = new DescribeLoadBalancersV2Request

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator {
      override def next() = {
        val response = backoffRequest {
          ctx.awsClient.elbv2.describeLoadBalancers(request.withMarker(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextMarker)
        response.getLoadBalancers.asScala
          .map(
            item => {
              val lr = new DescribeListenersRequest().withLoadBalancerArn(item.getLoadBalancerArn)
              val listeners =
                backoffRequest { ctx.awsClient.elbv2.describeListeners(lr) }.getListeners
              // If there are no listeners AWS returns null instead of an empty list
              val listenersList =
                if (listeners == null) Nil
                else listeners.asScala.map(item => ctx.beanMapper(item)).toList
              val data = ctx.beanMapper(item).asInstanceOf[Map[String, Any]] ++ Map(
                "listeners" -> listenersList
              )

              Record(item.getLoadBalancerName, new DateTime(item.getCreatedTime), data)
            }
          )
          .toList
      }
    }
    it.toList.flatten
  }
}
