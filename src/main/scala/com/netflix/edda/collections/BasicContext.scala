package com.netflix.edda.collections

import com.netflix.edda.records.BasicRecordMatcher
import com.typesafe.scalalogging.StrictLogging

/** Simple collection context that loads /edda.properties to use
  * as the configuration.  It also uses the BasicRecordMatcher as
  * the recordMatcher
  */
object BasicContext extends Collection.Context with StrictLogging {
  lazy val recordMatcher = new BasicRecordMatcher
}
