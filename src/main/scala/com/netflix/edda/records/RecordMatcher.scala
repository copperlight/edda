package com.netflix.edda.records

/** simple interface to allow complex queries against in-memory record sets.
  * See [[BasicRecordMatcher]]
  */
trait RecordMatcher {
  def doesMatch(queryMap: Map[String, Any], record: Map[String, Any]): Boolean
}
