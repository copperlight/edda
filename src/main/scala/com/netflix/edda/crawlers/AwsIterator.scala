package com.netflix.edda.crawlers

import com.netflix.edda.records.Record

/** iterator interface for working with the paginated results from some
  * aws apis
  */
abstract class AwsIterator extends Iterator[Seq[Record]] {
  var nextToken: Option[String] = Some(null)
  def hasNext: Boolean = nextToken.isDefined
  def next(): List[Record]
}
