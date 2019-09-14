package com.netflix.edda.actors

import com.netflix.edda.util.Common

case class RequestId(id: String = Common.uuid) {
  override def toString: String = s"[$id] "
}
