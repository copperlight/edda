val aws = "1.11.579"
val jackson = "1.9.13"
val jersey = "1.19"
val log4j = "2.12.1"
val slf4j = "1.7.28"

lazy val edda = project
  .in(file("."))
  .configure(BuildSettings.profile)
  .enablePlugins(JettyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-autoscaling" % aws,
      "com.amazonaws" % "aws-java-sdk-cloudformation" % aws,
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % aws,
      "com.amazonaws" % "aws-java-sdk-core" % aws,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % aws,
      "com.amazonaws" % "aws-java-sdk-ec2" % aws,
      "com.amazonaws" % "aws-java-sdk-elasticache" % aws,
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % aws,
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancingv2" % aws,
      "com.amazonaws" % "aws-java-sdk-iam" % aws,
      "com.amazonaws" % "aws-java-sdk-lambda" % aws,
      "com.amazonaws" % "aws-java-sdk-rds" % aws,
      "com.amazonaws" % "aws-java-sdk-route53" % aws,
      "com.amazonaws" % "aws-java-sdk-s3" % aws,
      "com.amazonaws" % "aws-java-sdk-sqs" % aws,
      "com.amazonaws" % "aws-java-sdk-sts" % aws,
      "com.googlecode.java-diff-utils" % "diffutils" % "1.2.1",
      "com.netflix.archaius" % "archaius-core" % "0.7.6",
      "com.netflix.servo" % "servo-core" % "0.12.28",
      "com.spatial4j" % "spatial4j" % "0.3",
      "com.sun.jersey" % "jersey-core" % jersey,
      "com.sun.jersey" % "jersey-server" % jersey,
      "com.sun.jersey" % "jersey-servlet" % jersey,
      "com.typesafe.scala-logging" % "scala-logging_2.11" % "3.5.0",
      "commons-beanutils" % "commons-beanutils" % "1.9.3",
      "commons-collections" % "commons-collections" % "3.2.2",
      "commons-io" % "commons-io" % "2.4",
      "javax.ws.rs" % "jsr311-api" % "1.1.1",
      "joda-time" % "joda-time" % "2.10.3",
      "org.apache.httpcomponents" % "httpclient" % "4.5.9",
      "org.apache.logging.log4j" % "log4j-api" % log4j,
      "org.apache.logging.log4j" % "log4j-core" % log4j,
      "org.apache.logging.log4j" % "log4j-jcl" % log4j,
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j,
      "org.codehaus.jackson" % "jackson-core-asl" % jackson,
      "org.codehaus.jackson" % "jackson-mapper-asl" % jackson,
      "org.joda" % "joda-convert" % "2.2.0",
      "org.scala-lang" % "scala-actors" % scalaVersion.value,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"
    )
  )
