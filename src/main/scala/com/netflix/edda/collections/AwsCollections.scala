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
import com.netflix.edda.aws.AwsClient
import com.netflix.edda.collections.aws._
import com.netflix.edda.collections.group.GroupAutoScalingGroups
import com.netflix.edda.collections.view.ViewInstanceCollection
import com.netflix.edda.crawlers.AwsCrawler
import com.netflix.edda.electors.Elector
import com.netflix.edda.mappers.BeanMapper
import com.netflix.edda.records.Record
import com.netflix.edda.records.RecordMatcher
import com.netflix.edda.util.Common
import org.slf4j.LoggerFactory

/** Factory to build all known AWS collections
  */
object AwsCollectionBuilder {

  /** builder routine to build collections
    *
    * CollectionManager.register is called for each generated collection object
    *
    * @param ctx a common collection context to be used by all collections
    * @param clientFactory  function that takes the account string and returns an AwsClient object to be used by all collections
    * @param bm beanMapper to be used by all the AWS crawlers
    * @param elector elector to be used for leadership selection
    */
  def buildAll(
    ctx: Collection.Context,
    clientFactory: String => AwsClient,
    bm: BeanMapper,
    elector: Elector
  ) {
    val collMap = Common.getProperty("edda", "accounts", "", "").get match {
      case "" =>
        val context: AwsCollection.Context = new AwsCollection.Context {
          val beanMapper: BeanMapper = bm
          val recordMatcher: RecordMatcher = ctx.recordMatcher
          val awsClient: AwsClient = clientFactory("")
        }

        mkCollections(context, "", elector)
          .map(collection => collection.rootName -> collection)
          .toMap

      case accountString =>
        val accounts = accountString.split(",")
        val accountContexts = accounts
          .map(
            account =>
              account -> new AwsCollection.Context {
                val beanMapper: BeanMapper = bm
                val recordMatcher: RecordMatcher = ctx.recordMatcher
                val awsClient: AwsClient = clientFactory(account)
            }
          )
          .toMap

        // this give us:
        // Map[String,Array[com.netflix.edda.collections.Collection]]
        val accountCollections = accounts
          .flatMap(account => {
            mkCollections(accountContexts(account), account, elector)
              .map(collection => collection.rootName -> collection)
          })
          .groupBy(_._1)
          .mapValues(c => c.map(x => x._2))

        // now map the Array's to a MergedCollection
        // need to return name -> _ for each array element
        // concat with MergedCollection(Array)
        accountCollections.flatMap(pair => {
          val name = pair._1
          val collections = pair._2
          collections.map(coll => coll.name -> coll).toMap ++ Map(
            name -> new MergedCollection(name, collections)
          )
        })
    }

    collMap.foreach(pair => CollectionManager.register(pair._1, pair._2))
  }

  /** called by *buildAll* for each account listed in the config to
    * generate the Collection objects
    */
  def mkCollections(
    ctx: AwsCollection.Context,
    accountName: String,
    elector: Elector
  ): Seq[RootCollection] = {
    val res = new AwsReservationCollection(accountName, elector, ctx)
    val elb = new AwsLoadBalancerCollection(accountName, elector, ctx)
    val asg = new AwsAutoScalingGroupsCollection(accountName, elector, ctx)
    val inst = new ViewInstanceCollection(res.crawler, accountName, elector, ctx)
    val hostedZones = new AwsHostedZoneCollection(accountName, elector, ctx)
    val hostedRecords =
      new AwsHostedRecordCollection(hostedZones.crawler, accountName, elector, ctx)

    Seq(
      new AwsAddressCollection(accountName, elector, ctx),
      asg,
      new AwsScalingPolicyCollection(accountName, elector, ctx),
      new AwsAlarmCollection(accountName, elector, ctx),
      new AwsImageCollection(accountName, elector, ctx),
      elb,
      new AwsInstanceHealthCollection(elb.crawler, accountName, elector, ctx),
      new AwsLaunchConfigurationCollection(accountName, elector, ctx),
      res,
      inst,
      new AwsSecurityGroupCollection(accountName, elector, ctx),
      new AwsSnapshotCollection(accountName, elector, ctx),
      new AwsTagCollection(accountName, elector, ctx),
      new AwsVolumeCollection(accountName, elector, ctx),
      new AwsBucketCollection(accountName, elector, ctx),
      new AwsIamUserCollection(accountName, elector, ctx),
      new AwsIamGroupCollection(accountName, elector, ctx),
      new AwsIamRoleCollection(accountName, elector, ctx),
      new AwsIamPolicyCollection(accountName, elector, ctx),
      new AwsIamVirtualMFADeviceCollection(accountName, elector, ctx),
      new AwsSimpleQueueCollection(accountName, elector, ctx),
      new AwsReservedInstanceCollection(accountName, elector, ctx),
      new GroupAutoScalingGroups(asg, inst, elector, ctx),
      hostedZones,
      hostedRecords,
      new AwsDatabaseCollection(accountName, elector, ctx),
      new AwsDatabaseSubnetCollection(accountName, elector, ctx),
      new AwsCacheClusterCollection(accountName, elector, ctx),
      new AwsSubnetCollection(accountName, elector, ctx),
      new AwsCloudformationCollection(accountName, elector, ctx),
      new AwsScalingActivitiesCollection(accountName, elector, ctx),
      new AwsScheduledActionsCollection(accountName, elector, ctx),
      new AwsVpcCollection(accountName, elector, ctx),
      new AwsReservedInstancesOfferingCollection(accountName, elector, ctx),
      new AwsLoadBalancerV2Collection(accountName, elector, ctx),
      new AwsTargetGroupCollection(accountName, elector, ctx)
    )
  }
}

/** static routines to be shared for AWS related collections */
object AwsCollection {

  abstract class Context() extends Collection.Context with AwsCrawler.Context

  private[this] val logger = LoggerFactory.getLogger(getClass)

  /** constructs list of Instances to used for the group* collections
    *
    * @param asgRec the ASG Record to get instance details from
    * @param instanceMap the list of know instance records
    * @return a sequence of Maps containing instance details
    */
  def makeGroupInstances(asgRec: Record, instanceMap: Map[String, Record])(
    implicit req: RequestId
  ): Seq[Map[String, Any]] = {
    val instances =
      asgRec.data.asInstanceOf[Map[String, Any]]("instances").asInstanceOf[List[Map[String, Any]]]
    val newInstances = instances
      .filter(inst => {
        val id = inst("instanceId").asInstanceOf[String]
        val bool = instanceMap.contains(id)
        if (!bool) {
          if (logger.isWarnEnabled)
            logger.warn(s"$req asg: ${asgRec.id} contains unknown instance: $id")
        }
        bool
      })
      .map(asgInst => {
        val id = asgInst("instanceId").asInstanceOf[String]
        val instance = instanceMap(id)
        val instanceData = instance.data.asInstanceOf[Map[String, Any]]
        Map(
          "availabilityZone" -> asgInst("availabilityZone"),
          "imageId"          -> instanceData.getOrElse("imageId", null),
          "instanceId"       -> id,
          "instanceType"     -> instanceData.getOrElse("instanceType", null),
          "launchTime"       -> instance.ctime,
          "lifecycleState"   -> asgInst("lifecycleState"),
          "platform"         -> instanceData.getOrElse("platform", null),
          "privateIpAddress" -> instanceData.getOrElse("privateIpAddress", null),
          "publicDnsName"    -> instanceData.getOrElse("publicDnsName", null),
          "publicIpAddress"  -> instanceData.getOrElse("publicIpAddress", null),
          "start"            -> instance.ctime,
          "vpcId"            -> instanceData.getOrElse("vpcId", null)
        )
      })
    newInstances
  }
}
