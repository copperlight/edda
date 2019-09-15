package com.netflix.edda.mappers

import com.amazonaws.services.ec2.model.InstanceState
import com.netflix.edda.util.Common

/** specialized [[BeanMapper]] trait that can suppress specific AWS resource tags
  * based on patterns expressed in the config.  This is necessary in case people add tags to
  * resources that change frequenly (like timestamps).  The AwsBeanMapper trait also works around
  * some internal state exposed in the bean for [[com.amazonaws.services.ec2.model.InstanceState]]
  * *code* field.
  */
trait AwsBeanMapper extends BeanMapper {
  val basicBeanMapper = new BasicBeanMapper

  val suppressSet: Set[String] =
    Common.getProperty("edda.crawler", "aws.suppressTags", "", "").get.split(",").toSet

  val suppressKeyMapper: KeyMapper = {
    case (obj: com.amazonaws.services.ec2.model.Tag, "value", Some(_: Any))
        if suppressSet.contains(obj.getKey) =>
      Some("[EDDA_SUPPRESSED]")
    case (obj: com.amazonaws.services.ec2.model.TagDescription, "value", Some(_: Any))
        if suppressSet.contains(obj.getKey) =>
      Some("[EDDA_SUPPRESSED]")
    case (obj: com.amazonaws.services.autoscaling.model.Tag, "value", Some(_: Any))
        if suppressSet.contains(obj.getKey) =>
      Some("[EDDA_SUPPRESSED]")
    case (obj: com.amazonaws.services.autoscaling.model.TagDescription, "value", Some(_: Any))
        if suppressSet.contains(obj.getKey) =>
      Some("[EDDA_SUPPRESSED]")
  }

  basicBeanMapper.addKeyMapper(suppressKeyMapper)

  def flattenTag(obj: Map[String, Any]): Map[String, Any] = {
    obj + (obj("key").asInstanceOf[String] -> obj("value"))
  }

  // this will flatten the tags so that we will have: { key -> a, value -> b, a -> b }
  val tagObjMapper: ObjMapper = {
    case obj: com.amazonaws.services.ec2.model.Tag =>
      flattenTag(basicBeanMapper.fromBean(obj).asInstanceOf[Map[String, Any]])
    case obj: com.amazonaws.services.ec2.model.TagDescription =>
      flattenTag(basicBeanMapper.fromBean(obj).asInstanceOf[Map[String, Any]])
    case obj: com.amazonaws.services.autoscaling.model.Tag =>
      flattenTag(basicBeanMapper.fromBean(obj).asInstanceOf[Map[String, Any]])
    case obj: com.amazonaws.services.autoscaling.model.TagDescription =>
      flattenTag(basicBeanMapper.fromBean(obj).asInstanceOf[Map[String, Any]])
    case obj: com.amazonaws.services.elasticloadbalancing.model.Tag =>
      flattenTag(basicBeanMapper.fromBean(obj).asInstanceOf[Map[String, Any]])
    case obj: com.amazonaws.services.rds.model.Tag =>
      flattenTag(basicBeanMapper.fromBean(obj).asInstanceOf[Map[String, Any]])
  }

  this.addObjMapper(tagObjMapper)

  val instanceStateKeyMapper: KeyMapper = {
    case (_: InstanceState, "code", Some(value: Int)) =>
      Some(0x00FF & value)
  }

  this.addKeyMapper(instanceStateKeyMapper)
}
