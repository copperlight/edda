package com.netflix.edda.crawlers.aws

import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.crawlers.AwsIterator
import com.netflix.edda.crawlers.Crawler
import com.netflix.edda.records.Record
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/** crawler for LaunchConfigurations
  *
  * @param name name of collection we are crawling for
  * @param ctx context to provide beanMapper
  */
class AwsLaunchConfigurationCrawler(val name: String, val ctx: AwsCrawler.Context) extends Crawler {
  val request = new DescribeLaunchConfigurationsRequest
  request.setMaxRecords(50)

  override def doCrawl()(implicit req: RequestId) = {
    val it = new AwsIterator() {
      def next() = {
        val response = backoffRequest {
          ctx.awsClient.asg.describeLaunchConfigurations(request.withNextToken(this.nextToken.get))
        }
        this.nextToken = Option(response.getNextToken)
        response.getLaunchConfigurations.asScala
          .map(
            item =>
              Record(
                item.getLaunchConfigurationName,
                new DateTime(item.getCreatedTime),
                ctx.beanMapper(item)
              )
          )
          .toList
      }
    }
    it.toList.flatten
  }
}
