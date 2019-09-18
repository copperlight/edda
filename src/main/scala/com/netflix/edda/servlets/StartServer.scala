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
package com.netflix.edda.servlets

import com.netflix.edda.actors.RequestId
import com.netflix.edda.aws.AwsClient
import com.netflix.edda.collections.AwsCollectionBuilder
import com.netflix.edda.collections.BasicContext
import com.netflix.edda.collections.CollectionManager
import com.netflix.edda.electors.Elector
import com.netflix.edda.mappers.AwsBeanMapper
import com.netflix.edda.mappers.BasicBeanMapper
import com.netflix.edda.util.Common
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import javax.servlet.http.HttpServlet

class StartServer extends HttpServlet with StrictLogging {

  implicit val req: RequestId = RequestId("init")

  override def init() {
    logger.info(s"$req Starting Server")

    val config = ConfigFactory.load()

    val electorClassName = {
      Common.getProperty(
        config,
        "edda",
        "elector.class",
        "",
        "com.netflix.edda.aws.DynamoDBElector"
      )
    }

    val electorClass = this.getClass.getClassLoader.loadClass(electorClassName)
    val elector = electorClass.newInstance.asInstanceOf[Elector]
    val bm = new BasicBeanMapper with AwsBeanMapper
    val awsClientFactory = (config: Config, account: String) => new AwsClient(config, account)

    AwsCollectionBuilder.buildAll(config, BasicContext, awsClientFactory, bm, elector)

    logger.info(s"$req Starting Collections")
    CollectionManager.start()

    super.init()
  }

  override def destroy() {
    CollectionManager.stop()
    super.destroy()
  }
}
