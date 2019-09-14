package com.netflix.edda.collections

import com.netflix.edda.actors.Queryable
import com.netflix.edda.actors.RequestId
import com.typesafe.scalalogging.StrictLogging

object CollectionManager extends StrictLogging {

  var collections: Map[String, Queryable] = Map()

  def register(name: String, collection: Queryable) {
    logger.info(s"Registering collection $collection")
    collections = collections + (name -> collection)
  }

  def get(name: String): Option[Queryable] = {
    collections.get(name)
  }

  def names(): Set[String] = collections.keySet

  def start()(implicit req: RequestId) {
    logger.info(s"$req Starting collections")
    collections.values.foreach(_.start())
  }

  def stop()(implicit req: RequestId) {
    logger.info(s"$req Stopping collections")
    collections.values.foreach(_.stop())
  }
}
