package com.netflix.edda.collections.view

import com.netflix.edda.collections.AwsCollection
import com.netflix.edda.collections.RootCollection
import com.netflix.edda.crawlers.aws.AwsSimpleQueueCrawler
import com.netflix.edda.electors.Elector
import com.netflix.edda.records.Record

class ViewSimpleQueuesCollection(
  val accountName: String,
  val elector: Elector,
  override val ctx: AwsCollection.Context
) extends RootCollection("view.simpleQueues", accountName, ctx) {

  val crawler = new AwsSimpleQueueCrawler(name, ctx)

  /** this is overridden from com.netflix.edda.aws.Collection because there are several
    * keys like ApproximateNumberOfMessages that are changing constantly.  We want to record
    * those changes, but not create new document revisions if the only changes are Approx* values
    */
  override protected def newStateTimeForChange(newRec: Record, oldRec: Record): Boolean = {
    if (newRec == null || oldRec == null) return true
    val newData = newRec.data.asInstanceOf[Map[String, Any]]
    val oldData = oldRec.data.asInstanceOf[Map[String, Any]]
    val newNoApprox = newRec.copy(
      data = newData + ("attributes" -> newData("attributes")
        .asInstanceOf[Map[String, String]]
        .filterNot(_._1.startsWith("Approx")))
    )
    val oldNoApprox = oldRec.copy(
      data = oldData + ("attributes" -> oldData("attributes")
        .asInstanceOf[Map[String, String]]
        .filterNot(_._1.startsWith("Approx")))
    )
    newRec.data != oldRec.data && newNoApprox.dataString != oldNoApprox.dataString
  }
}
