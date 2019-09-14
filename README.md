[![Build Status](https://travis-ci.org/Netflix/edda.svg)](https://travis-ci.org/Netflix/edda/builds)

## Description

Edda is a API read caching service for AWS.

It crawls AWS APIs on a regular schedule, flattens the results and stores them in-memory, so that they can be served
quickly to clients. As long as your AWS account throttle limits are scaled for the Edda use case, and your internal
cloud applications use Edda collections for reading AWS data, then additional throttle warnings should be minimal.

## Documentation

* See the [wiki](https://github.com/Netflix/edda/wiki) for a project overview.
* Example [IAM Role](./docs/iamrole-example.md) definitions.
* Example [Tomcat](./docs/tomcat-example.md) configurations.

## Support

[Edda Google Group](http://groups.google.com/group/edda-users)

## Local Development

To run the service locally, using [xsbt-web-plugin]:

```bash
export AWS_ACCESS_KEY_ID=yourAccessKey
export AWS_SECRET_KEY=yourSecretKey
export EC2_REGION=yourRegion
export NETFLIX_STACK=yourStack

sbt

> jetty:start
```

When running locally, the following datastores are used in the AWS account and region specified by the environment
variables:

| Type | Name |
|------|------|
| DynamoDB Leader Table | tbd |
| DynamoDB S3 Table | tbd |
| S3 Bucket | tbd |

[xsbt-web-plugin]: https://github.com/earldouglas/xsbt-web-plugin/tree/master/docs/examples/getting-started

## License

```
/**
 *
 *  Copyright 2019 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
```
