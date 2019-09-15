/*
 * Copyright 2012-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.edda.crawlers

import com.netflix.edda.aws.AwsClient
import com.netflix.edda.mappers.BeanMapper

/** static namespace for out Context trait */
object AwsCrawler {

  /** All AWS Crawlers require the basic ConfigContext
    * plus an [[com.netflix.edda.aws.AwsClient]] and [[BeanMapper]]
    */
  trait Context {
    def awsClient: AwsClient
    def beanMapper: BeanMapper
  }

}
