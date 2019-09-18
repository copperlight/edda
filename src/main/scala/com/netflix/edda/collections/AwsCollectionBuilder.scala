package com.netflix.edda.collections

import com.netflix.edda.actors.Queryable
import com.netflix.edda.aws.AvailabilityZones
import com.netflix.edda.aws.AwsClient
import com.netflix.edda.collections.aws.AwsAddressesCollection
import com.netflix.edda.collections.aws.AwsAlarmsCollection
import com.netflix.edda.collections.aws.AwsAutoScalingGroupsCollection
import com.netflix.edda.collections.aws.AwsBucketsCollection
import com.netflix.edda.collections.aws.AwsCacheClustersCollection
import com.netflix.edda.collections.aws.AwsStacksCollection
import com.netflix.edda.collections.aws.AwsDbInstancesCollection
import com.netflix.edda.collections.aws.AwsDbSubnetsCollection
import com.netflix.edda.collections.aws.AwsHostedRecordsCollection
import com.netflix.edda.collections.aws.AwsHostedZonesCollection
import com.netflix.edda.collections.aws.AwsIamGroupsCollection
import com.netflix.edda.collections.aws.AwsIamPoliciesCollection
import com.netflix.edda.collections.aws.AwsIamRolesCollection
import com.netflix.edda.collections.aws.AwsIamUsersCollection
import com.netflix.edda.collections.aws.AwsIamVirtualMFADevicesCollection
import com.netflix.edda.collections.aws.AwsImagesCollection
import com.netflix.edda.collections.aws.AwsLoadBalancerInstancesCollection
import com.netflix.edda.collections.aws.AwsLaunchConfigurationsCollection
import com.netflix.edda.collections.aws.AwsLoadBalancersCollection
import com.netflix.edda.collections.aws.AwsLoadBalancersV2Collection
import com.netflix.edda.collections.aws.AwsInstancesCollection
import com.netflix.edda.collections.aws.AwsReservedInstancesCollection
import com.netflix.edda.collections.aws.AwsReservedInstancesOfferingsCollection
import com.netflix.edda.collections.aws.AwsScalingActivitiesCollection
import com.netflix.edda.collections.aws.AwsScalingPoliciesCollection
import com.netflix.edda.collections.aws.AwsScheduledActionsCollection
import com.netflix.edda.collections.aws.AwsSecurityGroupsCollection
import com.netflix.edda.collections.aws.AwsSnapshotsCollection
import com.netflix.edda.collections.aws.AwsSubnetsCollection
import com.netflix.edda.collections.aws.AwsTagsCollection
import com.netflix.edda.collections.aws.AwsTargetGroupsCollection
import com.netflix.edda.collections.aws.AwsVolumesCollection
import com.netflix.edda.collections.aws.AwsVpcsCollection
import com.netflix.edda.collections.group.GroupAutoScalingGroups
import com.netflix.edda.collections.view.ViewInstancesCollection
import com.netflix.edda.collections.view.ViewSimpleQueuesCollection
import com.netflix.edda.electors.Elector
import com.netflix.edda.mappers.BeanMapper
import com.netflix.edda.records.RecordMatcher
import com.netflix.edda.util.Common
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

object AwsCollectionBuilder extends StrictLogging {

  def buildAll(
    config: Config,
    ctx: Collection.Context,
    clientFactory: (Config, String) => AwsClient,
    bm: BeanMapper,
    elector: Elector
  ): Unit = {

    val accountsProp: String = Common.getProperty(config, "edda", "accounts", "", "")

    def context(account: String): AwsCollection.Context = new AwsCollection.Context {
      val beanMapper: BeanMapper = bm
      val recordMatcher: RecordMatcher = ctx.recordMatcher
      val awsClient: AwsClient = clientFactory(account)
    }

    val collMap: Map[String, Queryable] = accountsProp match {
      case "" =>
        mkCollections(context(""), "", elector)
          .map(collection => collection.rootName -> collection)
          .toMap

      case accountString =>
        val accounts: Array[String] = accountString.split(",")

        val accountCollections: Map[String, Array[RootCollection]] = accounts
          .flatMap(account => {
            mkCollections(context(account), account, elector)
              .map(collection => collection.rootName -> collection)
          })
          .groupBy(_._1)
          .mapValues(c => c.map(x => x._2))

        accountCollections.flatMap {
          case (name, collections) =>
            collections.map(coll => coll.name -> coll).toMap ++
              Map(name -> new MergedCollection(name, collections))
        }
    }

    collMap.foreach {
      case (name, collection) => CollectionManager.register(name, collection)
    }
  }

  def mkCollections(
    ctx: AwsCollection.Context,
    accountName: String,
    elector: Elector
  ): Seq[RootCollection] = {

    logger.info(s"ctx=$ctx accountName=$accountName elector=$elector")

    // format: off
    val awsZones         = new AvailabilityZones(ctx)

    val awsAsgs          = new AwsAutoScalingGroupsCollection(accountName, elector, ctx)

    val awsElbs          = new AwsLoadBalancersCollection(accountName, elector, ctx)

    val awsHostedZones   = new AwsHostedZonesCollection(accountName, elector, ctx)
    val awsHostedRecords = new AwsHostedRecordsCollection(awsHostedZones.crawler, accountName, elector, ctx)

    val awsReservations  = new AwsInstancesCollection(accountName, elector, ctx)
    val viewInstances    = new ViewInstancesCollection(awsReservations.crawler, accountName, elector, ctx)

    Seq(
      // collections without dependencies
      new AwsAddressesCollection(accountName, elector, ctx),
      new AwsAlarmsCollection(accountName, elector, ctx),
      new AwsBucketsCollection(accountName, elector, ctx),
      new AwsCacheClustersCollection(accountName, elector, ctx),
      new AwsStacksCollection(accountName, elector, ctx),
      new AwsDbInstancesCollection(accountName, elector, ctx),
      new AwsDbSubnetsCollection(accountName, elector, ctx),
      new AwsIamGroupsCollection(accountName, elector, ctx),
      new AwsIamPoliciesCollection(accountName, elector, ctx),
      new AwsIamRolesCollection(accountName, elector, ctx),
      new AwsIamUsersCollection(accountName, elector, ctx),
      new AwsIamVirtualMFADevicesCollection(accountName, elector, ctx),
      new AwsImagesCollection(accountName, elector, ctx),
      new AwsLaunchConfigurationsCollection(accountName, elector, ctx),
      new AwsLoadBalancersV2Collection(accountName, elector, ctx),
      new AwsReservedInstancesCollection(accountName, elector, ctx),
      new AwsReservedInstancesOfferingsCollection(accountName, elector, ctx),
      new AwsScalingActivitiesCollection(accountName, elector, ctx),
      new AwsScalingPoliciesCollection(accountName, elector, ctx),
      new AwsScheduledActionsCollection(accountName, elector, ctx),
      new AwsSecurityGroupsCollection(accountName, elector, ctx),
      new ViewSimpleQueuesCollection(accountName, elector, ctx),
      new AwsSnapshotsCollection(accountName, elector, ctx),
      new AwsSubnetsCollection(accountName, elector, ctx),
      new AwsTagsCollection(accountName, elector, ctx),
      new AwsTargetGroupsCollection(accountName, elector, ctx),
      new AwsVolumesCollection(accountName, elector, ctx),
      new AwsVpcsCollection(accountName, elector, ctx),
      // collections which provide information to other collections
      awsAsgs,
      awsElbs,
      awsHostedRecords,
      awsHostedZones,
      awsReservations,
      viewInstances,
      // collections which depend on information from other collections
      new AwsLoadBalancerInstancesCollection(awsElbs.crawler, accountName, elector, ctx),
      new GroupAutoScalingGroups(awsAsgs, viewInstances, elector, ctx)
    )
    // format: on
  }
}
