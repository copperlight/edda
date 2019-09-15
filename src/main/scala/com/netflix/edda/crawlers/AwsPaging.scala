package com.netflix.edda.crawlers

import com.netflix.iep.aws.Pagination

import scala.collection.JavaConverters._

object AwsPaging {

  /** Helper to make the java8 function stuff more idiomatic to scala. Since edda is stuck
    * on 2.11, the java8 interop is not as refined.
    *
    */
  def iterator[R, T](request: R, client: R => T): Iterator[T] = {
    val f = new java.util.function.Function[R, T] {
      override def apply(r: R): T = client(r)
    }

    Pagination.createIterator[R, T](request, f).asScala
  }
}
