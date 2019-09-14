package com.netflix.edda.collections

/** for setting the name on Collections when we are tracking many accounts with same root name.  Used with MergedCollection
  * so we could have test.us-east-1.aws.autoScalingGroups and test.us-west-1.aws.autoScalingGroups independent collections
  * but then have a MergedCollection called "aws.autoScalingGroups" that will dispatch queries to both collections.
  *
  * @param rootName base name of Collection (ie aws.autoScalingGroups)
  * @param accountName name of account (ie test.us-east-1)
  * @param ctx the collection context for recordMatcher
  */
abstract class RootCollection(
  val rootName: String,
  accountName: String,
  ctx: Collection.Context
) extends Collection(ctx) {

  val name: String = accountName match {
    case ""        => rootName
    case x: String => s"$x.$rootName"
  }
}
