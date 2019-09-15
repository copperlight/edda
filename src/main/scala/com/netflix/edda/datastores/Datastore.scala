package com.netflix.edda.datastores

import com.netflix.edda.actors.Queryable
import com.netflix.edda.actors.RequestId
import com.netflix.edda.collections.Collection
import com.netflix.edda.records.Record
import com.netflix.edda.records.RecordSet

/** basic interface for data stores to persist Crawler/Collection state */
trait Datastore {

  /** setup data store connections */
  def init()

  /** perform query on data store, see [[Queryable.query]] */
  def query(
    queryMap: Map[String, Any],
    limit: Int,
    keys: Set[String],
    replicaOk: Boolean
  )(implicit req: RequestId): Seq[Record]

  /** load records from data store, used at Collection start-up to prime in-memory cache and to refresh
    * in-memory cache when we are not the leader
    *
    * @param replicaOk specify if we can load from a read-replica in the data store when there are
    *                  redundant systems running for high-availability.
    */
  def load(replicaOk: Boolean)(implicit req: RequestId): RecordSet

  /** make changes to the data store depending on the Collection delta found after a Crawl result */
  def update(d: Collection.Delta)(implicit req: RequestId): Collection.Delta

  /** remove records that match the query */
  def remove(queryMap: Map[String, Any])(implicit req: RequestId)
}
