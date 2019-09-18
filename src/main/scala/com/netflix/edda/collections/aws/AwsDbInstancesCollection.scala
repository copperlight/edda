package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsDbInstanceCrawler
import com.netflix.edda.electors.Elector
import com.netflix.edda.records.Record

class AwsDbInstancesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.dbInstances", accountName, ctx) {
  val crawler = new AwsDbInstanceCrawler(name, ctx)

  /** this is overridden from com.netflix.edda.aws.Collection because we want to record
    * changes, but not create new document revisions if the only changes are to latestRestorableTime values
    */
  override protected def newStateTimeForChange(newRec: Record, oldRec: Record): Boolean = {
    if (newRec == null || oldRec == null) return true
    val newData = newRec.data.asInstanceOf[Map[String, Any]]
    val oldData = oldRec.data.asInstanceOf[Map[String, Any]]
    val newLatestRestorable =
      newRec.copy(data = newData.filterNot(_._1.startsWith("latestRestorable")))
    val oldLatestRestorable =
      oldRec.copy(data = oldData.filterNot(_._1.startsWith("latestRestorable")))
    newRec.data != oldRec.data && newLatestRestorable.dataString != oldLatestRestorable.dataString
  }
}
