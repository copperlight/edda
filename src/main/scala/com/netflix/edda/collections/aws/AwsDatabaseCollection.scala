package com.netflix.edda.collections.aws

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.AwsDatabaseCrawler
import com.netflix.edda.electors.Elector
import com.netflix.edda.records.Record

/** collection for AWS RDS database instances
  *
  * root collection name: aws.databases
  *
  * see crawler details [[AwsDatabaseCrawler]]
  *
  * @param accountName account name to be prefixed to collection name
  * @param elector Elector to determine leadership
  * @param ctx context for AWS clients objects
  */
class AwsDatabaseCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("aws.databases", accountName, ctx) {
  val crawler = new AwsDatabaseCrawler(name, ctx)

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
