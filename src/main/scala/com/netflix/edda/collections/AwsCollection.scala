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
package com.netflix.edda.collections

import com.netflix.edda.actors.RequestId
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.records.Record
import com.typesafe.scalalogging.StrictLogging

object AwsCollection extends StrictLogging {

  abstract class Context() extends Collection.Context with AwsCrawler.Context

  /** Make a seq of instances, with details combined from the aws.autoScalingGroups collection
    * and the view.instances collection.
    *
    * This blends the AutoScalingGroup Instance model with the EC2 Instance model.
    *
    * At the filter stage, there can be a fair number of unrecognized instances, due to lag in
    * crawling instances. In large accounts, it can take more than 5 min to crawl instances
    * and they will appear in the ASG list before the instance crawler reports them. It is not
    * worth logging these cases, since they will self-correct on subsequent updates.
    *
    * @param asg
    *   A map describing an AutoScalingGroup, from the aws.autoScalingGroup crawler.
    * @param ec2RecordMap
    *   A map of (instanceId -> EC2 Instance Record) queried from the view.instances collection.
    * @param req
    *   The RequestId responsible for generating this call.
    * @return
    *   A seq of instance maps, suitable for the group.autoScalingGroups endpoint.
    */
  def makeGroupInstances(
    asg: Record,
    ec2RecordMap: Map[String, Record]
  )(implicit req: RequestId): Seq[Map[String, Any]] = {

    asg.data
      .asInstanceOf[Map[String, Any]]("instances")
      .asInstanceOf[List[Map[String, Any]]]
      .filter { asgInstance =>
        val instanceId = asgInstance("instanceId").asInstanceOf[String]
        ec2RecordMap.contains(instanceId)
      }
      .map { asgInstance =>
        val instanceId = asgInstance("instanceId").asInstanceOf[String]
        val ec2Record = ec2RecordMap(instanceId)
        val ec2Instance = ec2Record.data.asInstanceOf[Map[String, Any]]

        Map(
          "availabilityZone" -> asgInstance("availabilityZone"),
          "imageId"          -> ec2Instance.get("imageId").orNull,
          "instanceId"       -> instanceId,
          "instanceType"     -> ec2Instance.get("instanceType").orNull,
          "launchTime"       -> ec2Record.ctime,
          "lifecycleState"   -> asgInstance("lifecycleState"),
          "platform"         -> ec2Instance.get("platform").orNull,
          "privateIpAddress" -> ec2Instance.get("privateIpAddress").orNull,
          "publicDnsName"    -> ec2Instance.get("publicDnsName").orNull,
          "publicIpAddress"  -> ec2Instance.get("publicIpAddress").orNull,
          "start"            -> ec2Record.ctime,
          "vpcId"            -> ec2Instance.get("vpcId").orNull,
          "subnetId"         -> ec2Instance.get("subnetId").orNull
        )
      }
  }
}
