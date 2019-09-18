/*
 * Copyright 2012-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.edda.aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.elasticache.AmazonElastiCache
import com.amazonaws.services.elasticache.AmazonElastiCacheClientBuilder
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.rds.AmazonRDS
import com.amazonaws.services.rds.AmazonRDSClientBuilder
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.{elasticloadbalancingv2 => alb}
import com.netflix.edda.util.Common
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

class AwsClient(val config: Config, val account: String) extends StrictLogging {

  private val region =
    Common.getProperty(config, "edda", "region", account, "")
  private val roleArn =
    Common.getProperty(config, "edda", "aws.roleArn", account, "")
  private val roleSessionName =
    Common.getProperty(config, "edda", "aws.roleSessionName", account, "Edda")

  private val provider = {
    if (roleArn.isEmpty) {
      logger.info(s"edda.*.aws.roleArn is empty - using DefaultAWSCredentialsProvider")
      new DefaultAWSCredentialsProviderChain()
    } else {
      logger.info(
        s"using STS Provider region=$region roleArn=$roleArn roleSessionName=$roleSessionName"
      )

      val stsClient = AWSSecurityTokenServiceClientBuilder
        .standard()
        .withCredentials(new DefaultAWSCredentialsProviderChain())
        .withRegion(region)
        .build()

      new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, roleSessionName)
        .withStsClient(stsClient)
        .build()
    }
  }

  /** Base ClientConfiguration used for AWS clients.
    *
    * Set the following client configuration options:
    *
    * - Keep SDK Default for ConnectionTimeout at 10 seconds.
    * - Keep SDK Default for SocketTimeout at 50 seconds and allow overrides for slow resources.
    * - Override SDK Default for Max Error Retry to 10 retries.
    * - Override SDK Default for Gzip Compression from false to true.
    *
    * With exponential back-off, requests will hit the maximum delay of 20 seconds in 9 requests.
    *
    * This includes a default RetryPolicy with the following configuration:
    *
    * - SDK Default Retry Condition
    *
    *   - Never retry on requests with non-repeatable content;
    *   - Retry on client exceptions caused by IOException;
    *   - Retry on service exceptions that are either 500 internal server errors, 503 service
    *   unavailable errors, service throttling errors or clock skew errors.
    *
    * - SDK Default Back-off Strategy
    *
    *   Increases exponentially from a base delay of 100 ms up to a max delay of 20 seconds. A
    *   larger scale factor of 500 ms is applied upon service throttling exceptions.
    *
    * - SDK Default Max Retry Count = 3
    *
    * - Honor Max Error Retry In ClientConfig = true
    *
    *   Whether the policy should honor the setMaxErrorRetry(int) method.
    *
    * Tuning with AWS SDK Metrics:
    *
    * - Use the aws.request.httpClientReceiveResponseTime Spectator AWS metrics to tune the
    * socket timeout value, if needed
    *
    * @param timeout
    *   The amount of time to wait (in milliseconds) for data to be transferred over an
    *   established, open connection before the connection times out and is closed. The
    *   SDK default for this value is 50000, so replicate that as the default value for
    *   the parameter.
    * @return ClientConfiguration
    */
  def clientConfig(timeout: Int = 50000): ClientConfiguration = {
    new ClientConfiguration()
      .withSocketTimeout(timeout)
      .withMaxErrorRetry(10)
      .withGzip(true)
  }

  lazy val asg: AmazonAutoScaling = {
    AmazonAutoScalingClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig(60000))
      .withRegion(region)
      .build()
  }

  lazy val cf: AmazonCloudFormation = {
    AmazonCloudFormationClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val cw: AmazonCloudWatch = {
    AmazonCloudWatchClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  /** Standard DynamoDB client.
    *
    * - Retry condition, in order:
    *   - Never retry on requests with non-repeatable content.
    *   - Retry on client exceptions caused by IOException.
    *   - Retry on service exceptions that are either 500 internal server errors,
    *   503 service unavailable errors, service throttling errors or clock skew
    *   errors.
    * - Back-off strategy, which increases exponentially up to a maximum amount of delay.
    *   - Base delay is 25 ms.
    *   - Throttled base delay is 500ms.
    *   - Maximum backoff is 20 sec.
    * - Maximum error retries is 10.
    *
    */
  lazy val dynamoDb: AmazonDynamoDB = {
    AmazonDynamoDBClientBuilder
      .standard()
      .withCredentials(provider)
      .withRegion(region)
      .build()
  }

  lazy val ec2: AmazonEC2 = {
    AmazonEC2ClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  /**
    * The images collection is not paginated and must be serialized all at once. For most regions
    * and accounts, which do not have many images, the default client configuration is fine. For
    * a region where AMI operations take place, this collection can have thousands of resources.
    *
    * You can check the response times of the API as follows:, using awscli:
    *
    * time aws --cli-read-timeout 300 --region us-east-1 ec2 describe-images > images.out
    *
    * The snapshots collection is similar in nature and should also use this client.
    */
  lazy val ec2Images: AmazonEC2 = {
    AmazonEC2ClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig(300000))
      .withRegion(region)
      .build()
  }

  lazy val ec2NetworkInterfaces: AmazonEC2 = {
    AmazonEC2ClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig(120000))
      .withRegion(region)
      .build()
  }

  lazy val ec2Volumes: AmazonEC2 = {
    AmazonEC2ClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig(180000))
      .withRegion(region)
      .build()
  }

  lazy val elastiCache: AmazonElastiCache = {
    AmazonElastiCacheClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val elb: AmazonElasticLoadBalancing = {
    AmazonElasticLoadBalancingClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val elbv2: alb.AmazonElasticLoadBalancing = {
    alb.AmazonElasticLoadBalancingClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val iam: AmazonIdentityManagement = {
    AmazonIdentityManagementClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val idm: AmazonIdentityManagement = {
    AmazonIdentityManagementClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val lambda: AWSLambda = {
    AWSLambdaClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val rds: AmazonRDS = {
    AmazonRDSClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val r53: AmazonRoute53 = {
    AmazonRoute53ClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val s3: AmazonS3 = {
    AmazonS3ClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }

  lazy val sqs: AmazonSQS = {
    AmazonSQSClientBuilder
      .standard()
      .withCredentials(provider)
      .withClientConfiguration(clientConfig())
      .withRegion(region)
      .build()
  }
}
