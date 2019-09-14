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

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.elasticache.AmazonElastiCacheClient
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancingv2.{
  AmazonElasticLoadBalancingClient => AmazonElasticLoadBalancingV2Client
}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.rds.AmazonRDSClient
import com.amazonaws.services.route53.AmazonRoute53Client
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.sqs.AmazonSQSClient
import com.netflix.edda.util.Common

object AwsClient {

  def mkCredentialProvider(
    accessKey: String,
    secretKey: String,
    arn: String
  ): AWSCredentialsProvider = {

    val provider = if (accessKey.isEmpty) {
      new DefaultAWSCredentialsProviderChain()
    } else {
      new AWSCredentialsProvider() {
        def getCredentials = new BasicAWSCredentials(accessKey, secretKey)
        def refresh(): Unit = {}
      }
    }

    if (arn.isEmpty) {
      provider
    } else {
      new STSAssumeRoleSessionCredentialsProvider(provider, arn, "edda")
    }
  }
}

/** provides access to AWS service client objects
  *
  * @param provider provider used to connect to AWS services
  * @param region used to select endpoint for AWS services
  */
class AwsClient(val provider: AWSCredentialsProvider, val region: String) {

  var account = ""

  /** uses [[com.amazonaws.auth.AWSCredentials]] to create AWSCredentialsProvider
    *
    * @param credentials used to connect to AWS services
    * @param region to select endpoint for AWS services
    */
  def this(credentials: AWSCredentials, region: String) = {
    this(
      new AWSCredentialsProvider() {
        def getCredentials: AWSCredentials = credentials
        def refresh(): Unit = {}
      },
      region
    )
  }

  /** create credentials from config file for account
    *
    * @param account account name, used for looking up properties
    */
  def this(account: String) =
    this(
      AwsClient.mkCredentialProvider(
        Common.getProperty("edda", "aws.accessKey", account, "").get,
        Common.getProperty("edda", "aws.secretKey", account, "").get,
        Common.getProperty("edda", "aws.assumeRoleArn", account, "").get
      ),
      Common.getProperty("edda", "region", account, "").get
    )

  /** create credential from provided arguments
    *
    * @param accessKey for account access
    * @param secretKey for account access
    * @param region used to select endpoint for AWS service
    */
  def this(accessKey: String, secretKey: String, region: String) = {
    this(AwsClient.mkCredentialProvider(accessKey, secretKey, ""), region)
  }

  /** generate a resource arn */
  def arn(resourceAPI: String, resourceType: String, resourceName: String): String = {
    val sep = arnSeparator(resourceType)
    s"arn:aws:$resourceAPI:$region:$account:$resourceType$sep$resourceName"
  }

  def arnSeparator(t: String): String = {
    if (t == "loadbalancer") "/" else ":"
  }

  def getAccountNum: String = {
    val stsClient = new AWSSecurityTokenServiceClient()
    stsClient.getCallerIdentity(new GetCallerIdentityRequest()).getAccount
  }

  def loadAccountNum(): Unit = {
    this.setAccountNum(this.getAccountNum)
  }

  def setAccountNum(accountNumber: String) {
    this.account = accountNumber
  }

  def ec2: AmazonEC2Client = {
    val client = new AmazonEC2Client(provider)
    client.setEndpoint("ec2." + region + ".amazonaws.com")
    client
  }

  def asg: AmazonAutoScalingClient = {
    val client = new AmazonAutoScalingClient(provider)
    client.setEndpoint("autoscaling." + region + ".amazonaws.com")
    client
  }

  def elb: AmazonElasticLoadBalancingClient = {
    val client = new AmazonElasticLoadBalancingClient(provider)
    client.setEndpoint("elasticloadbalancing." + region + ".amazonaws.com")
    client
  }

  def elbv2: AmazonElasticLoadBalancingV2Client = {
    val client = new AmazonElasticLoadBalancingV2Client(provider)
    client.setEndpoint("elasticloadbalancing." + region + ".amazonaws.com")
    client
  }

  def s3: AmazonS3Client = {
    val client = new AmazonS3Client(provider)

    if (region == "us-east-1")
      client.setEndpoint("s3.amazonaws.com")
    else
      client.setEndpoint("s3-" + region + ".amazonaws.com")

    client
  }

  def idm: AmazonIdentityManagementClient = {
    val client = new AmazonIdentityManagementClient(provider)

    if (region == "us-gov")
      client.setEndpoint("iam.us-gov.amazonaws.com")
    else
      client.setEndpoint("iam.amazonaws.com")

    client
  }

  def sqs: AmazonSQSClient = {
    val client = new AmazonSQSClient(provider)
    client.setEndpoint("sqs." + region + ".amazonaws.com")
    client
  }

  def cw: AmazonCloudWatchClient = {
    val client = new AmazonCloudWatchClient(provider)
    client.setEndpoint("monitoring." + region + ".amazonaws.com")
    client
  }

  def r53: AmazonRoute53Client = {
    val client = new AmazonRoute53Client(provider)
    client.setEndpoint("route53.amazonaws.com")
    client
  }

  def rds: AmazonRDSClient = {
    val client = new AmazonRDSClient(provider)
    client.setEndpoint("rds." + region + ".amazonaws.com")
    client
  }

  def elasticache: AmazonElastiCacheClient = {
    val client = new AmazonElastiCacheClient(provider)
    client.setEndpoint("elasticache." + region + ".amazonaws.com")
    client
  }

  def dynamo: AmazonDynamoDBClient = {
    val client = new AmazonDynamoDBClient(provider)
    client.setEndpoint("dynamodb." + region + ".amazonaws.com")
    client
  }

  def cloudformation: AmazonCloudFormationClient = {
    val client = new AmazonCloudFormationClient(provider)
    client.setEndpoint("cloudformation." + region + ".amazonaws.com")
    client
  }
}
