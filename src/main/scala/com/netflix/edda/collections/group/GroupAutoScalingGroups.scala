package com.netflix.edda.collections.group

import com.netflix.edda.actors.RequestId
import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.Collection
import com.netflix.edda.collections.GroupCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.collections.aws.AwsAutoScalingGroupsCollection
import com.netflix.edda.collections.view.ViewInstanceCollection
import com.netflix.edda.crawlers.AwsAutoScalingGroupCrawler
import com.netflix.edda.electors.Elector
import com.netflix.edda.records.RecordSet
import com.netflix.edda.util.Common
import org.joda.time.DateTime

/** collection for abstracted groupings of instances in AutoScalingGroups
  *
  * root collection name: group.autoScalingGroups
  *
  * see crawler details [[AwsAutoScalingGroupCrawler]]
  *
  * @param asgCollection ASG collection where the crawler used comes from
  * @param instanceCollection Instance Collection so we can query for instance details
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class GroupAutoScalingGroups(
  val asgCollection: AwsAutoScalingGroupsCollection,
  val instanceCollection: ViewInstanceCollection,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("group.autoScalingGroups", asgCollection.accountName, ctx)
    with GroupCollection {
  // don't crawl, we get crawl results from the asgCollection crawler
  override val allowCrawl = false
  val crawler: AwsAutoScalingGroupCrawler = asgCollection.crawler

  // used in GroupCollection
  val mergeKeys: Map[String, String] = Map("instances" -> "instanceId")

  val instanceQuery: Map[String, Map[String, Seq[String]]] = {
    Map(
      "data.state.name" -> Map(
        "$nin" -> Seq("pending", "shutting-down", "terminating", "terminated")
      )
    )
  }

  /** The crawler is really a com.netflix.edda.crawlers.AwsAutoScalingGroupCrawler so we have to
    * translate the ASG records into what the group.autoScalingGroup records should look like
    *
    * @param newRecordSet the new ASG records from the crawler
    * @param oldRecordSet these are the previous generation of group.autoScalingGroup records
    * @return the [[Collection.Delta]] modified from com.netflix.edda.collections.GroupCollection.groupDelta
    */
  override protected[edda] def delta(newRecordSet: RecordSet, oldRecordSet: RecordSet)(
    implicit req: RequestId
  ): Collection.Delta = {
    // newRecords will be from the ASG crawler, we need to convert it
    // to the Group records then call groupDelta

    val slotMap = groupSlots(oldRecordSet.records)

    val records = try {
      scala.concurrent.Await.result(
        instanceCollection.query(instanceQuery),
        scala.concurrent.duration.Duration(
          60000,
          scala.concurrent.duration.MILLISECONDS
        )
      )
    } catch {
      case e: Throwable =>
        logger.error(s"failed to fetch instances for $name delta: $e")
        throw e
    }

    val instanceMap = records.map(rec => rec.id         -> rec).toMap
    val oldMap = oldRecordSet.records.map(rec => rec.id -> rec).toMap

    val modNewRecords = newRecordSet.records.map(asgRec => {
      val newInstances = assignSlots(
        AwsCollection.makeGroupInstances(asgRec, instanceMap),
        "instanceId",
        slotMap("instances")
      )

      val asgData = asgRec.data.asInstanceOf[Map[String, Any]]

      val ctime = oldMap.get(asgRec.id) match {
        case Some(rec) => rec.ctime
        case None      => asgRec.ctime
      }

      val data = Map(
        "desiredCapacity"         -> asgData.getOrElse("desiredCapacity", null),
        "instances"               -> newInstances,
        "launchConfigurationName" -> asgData.getOrElse("launchConfigurationName", null),
        "loadBalancerNames"       -> asgData.getOrElse("loadBalancerNames", List()),
        "maxSize"                 -> asgData.getOrElse("maxSize", null),
        "minSize"                 -> asgData.getOrElse("minSize", null),
        "name"                    -> asgRec.id,
        "start"                   -> ctime
      )

      asgRec.copy(data = data)
    })

    super.delta(RecordSet(modNewRecords, newRecordSet.meta), oldRecordSet)
  }

  override protected[edda] def update(
    d: Collection.Delta
  )(implicit req: RequestId): Collection.Delta = {
    if (dataStore.isDefined && (d.changed.nonEmpty || d.added.nonEmpty || d.removed.nonEmpty)) {
      // make sure slots are not reassigned
      // get SET of "old" instances
      val oldInstances = d.changed
        .flatMap(update => {
          val instances = update.oldRecord.data
            .asInstanceOf[Map[String, Any]]("instances")
            .asInstanceOf[Seq[Map[String, Any]]]
          instances.map(instance => instance("instanceId").asInstanceOf[String])
        })
        .toSet

      // get MAP of "new" instances -> slot (new updates + added records)
      val newInstances = (d.changed.map(update => update.newRecord) ++ d.added)
        .flatMap(rec => {
          val instances =
            rec.data.asInstanceOf[Map[String, Any]]("instances").asInstanceOf[Seq[Map[String, Any]]]
          instances.map(
            instance =>
              instance("instanceId").asInstanceOf[String] -> instance("slot").asInstanceOf[Int]
          )
        })
        .toMap

      // just get a list of the instances that we think are new (ie have had new slot assigned)
      val addedInstances = newInstances.keySet diff oldInstances

      if (addedInstances.nonEmpty) {
        // query db for all "new" instances to get slot
        val query = Map(
          "data.instances.instanceId" -> Map("$in" -> addedInstances.toSeq),
          "$or" -> List(
            Map("ltime" -> null),
            Map("ltime" -> Map("$gt" -> DateTime.now.minusDays(2)))
          )
        )

        val recs = dataStore.get.query(
          query,
          limit = 0,
          keys = Set("data.instances.instanceId", "data.instances.slot", "stime"),
          replicaOk = false
        )

        recs.foreach(rec => {
          val instances =
            rec.data.asInstanceOf[Map[String, Any]]("instances").asInstanceOf[Seq[Map[String, Any]]]
          instances.foreach(instance => {
            val id = instance("instanceId").asInstanceOf[String]
            if (newInstances.contains(id)) {
              val slot = instance("slot").asInstanceOf[Int]
              if (newInstances(id) != slot) {
                val msg = this.toString + " Slot reassignment for instance " + id + " from " + slot + " [stime=" + rec.stime.getMillis + "] to " + newInstances(
                  id
                )
                logger.error(s"$req $msg")
                if (!Common
                      .getProperty("edda", "collection.allowSlotReassign", name, "false")
                      .get
                      .toBoolean) {
                  throw new java.lang.RuntimeException(msg)
                }
              }
            }
          })
        })
      }
    }
    super.update(d)
  }
}
