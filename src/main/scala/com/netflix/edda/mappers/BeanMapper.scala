package com.netflix.edda.mappers

/** trait to allow for mapping beans to scala objects */
trait BeanMapper {
  def apply(obj: Any): Any

  def addKeyMapper(pf: PartialFunction[(AnyRef, String, Option[Any]), Option[Any]])

  def addObjMapper(pf: PartialFunction[AnyRef, AnyRef])
}
