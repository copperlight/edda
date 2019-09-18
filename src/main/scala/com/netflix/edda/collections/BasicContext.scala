package com.netflix.edda.collections

import com.netflix.edda.records.BasicRecordMatcher
import com.typesafe.scalalogging.StrictLogging

object BasicContext extends Collection.Context with StrictLogging {
  lazy val recordMatcher = new BasicRecordMatcher
}
