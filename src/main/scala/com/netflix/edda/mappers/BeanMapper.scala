package com.netflix.edda.mappers

/** trait to allow for mapping beans to scala objects */
trait BeanMapper {
  type KeyMapper = PartialFunction[(AnyRef, String, Option[Any]), Option[Any]]
  type ObjMapper = PartialFunction[AnyRef, AnyRef]

  def apply(obj: Any): Any
  def addKeyMapper(pf: KeyMapper)
  def addObjMapper(pf: ObjMapper)
}
