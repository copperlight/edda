## Introduction

Describes a Tomcat application server configuration suitable for Edda.

### server.xml

Replace the following placeholders:

* `$SHUTDOWN_PORT`
* `$HTTP_PORT`
* `$SERVER_NAME`
* `$AJP_PORT`

```xml
<?xml version="1.0" encoding="UTF-8"?>

<Server
  className="org.apache.catalina.core.StandardServer"
  port="$SHUTDOWN_PORT"
  shutdown="SHUTDOWN"
>

   <Service
     className="org.apache.catalina.core.StandardService"
     name="Catalina"
   >

      <Executor
         name="tomcatThreadPool"
         namePrefix="tomcat-http-"
         maxThreads="220"
         maxQueueSize="5"
      />

      <Connector
         port="$HTTP_PORT"
         protocol="HTTP/1.1"
         acceptCount="100"
         maxKeepAliveRequests="100"
         scheme="http"
         secure="false"
         tcpNoDelay="true"
         executor="tomcatThreadPool"
         address="0.0.0.0"
         compression="off"
         disableUploadTimeout="true"
         enableLookups="false"
         server="$SERVER_NAME"
       />

     <Connector
         port="$AJP_PORT"
         protocol="org.apache.coyote.ajp.AjpProtocol"
         acceptCount="100"
         scheme="http"
         secure="false"
         tcpNoDelay="true"
         URIEncoding="UTF-8"
         executor="tomcatThreadPool"
         maxConnections="220"
         address="0.0.0.0"
         packetSize="65536"
     />

      <Engine
        defaultHost="localhost"
        name="Catalina"
      >
        <Host
          appBase="webapps"
          autoDeploy="true"
          name="localhost"
          unpackWARs="true"
        />

      </Engine>
   </Service>
</Server>
```

## setenv.sh

This provides an example of JDK options you may want to set when running the Tomcat application server.

Edda runs best on instances with lots of memory, especially when there are many resources in your account.

* Specify `JAVA_HOME` as the path to your JDK.
* Specify `TOMCAT_HOME` as the path to your Tomcat installation.

Replace the following placeholders:

* `$METASPACE`
* `$CODE_CACHE`
* `$INITIAL_HEAP` == `$MAX_HEAP`
* `$YOUNG_GENERATION`

Given the nature of how Edda operates, the choice of `UseParallelOldGC` is key to performance. Depending upon your
instance size, you should be able to comfortably allocate 80-98 percent of your RAM to the Java heap, tuning the
sizes appropriately.

```bash
#!/bin/bash

JAVA_HOME=
JRE_HOME=$JAVA_HOME
TOMCAT_HOME=
CATALINA_PID=$TOMCAT_HOME/logs/catalina.pid

if [[ "$1" == "start" ]]; then

   JAVA_OPTS=" \
       -server \
       -verbose:gc \
       -XX:+DisableExplicitGC \
       -XX:+ExplicitGCInvokesConcurrent \
       -XX:+HeapDumpOnOutOfMemoryError \
       -XX:+PreserveFramePointer \
       -XX:+PrintAdaptiveSizePolicy \
       -XX:+PrintCommandLineFlags \
       -XX:+PrintGCDateStamps \
       -XX:+PrintGCDetails \
       -XX:+UseParallelOldGC \
       -XX:-UseAdaptiveSizePolicy \
       -XX:ErrorFile=$TOMCAT_HOME/logs/hs_err_%p.log \
       -XX:HeapDumpPath=$TOMCAT_HOME/logs \
       -XX:MaxMetaspaceSize=$METASPACE \
       -XX:ReservedCodeCacheSize=$CODE_CACHE \
       -Xloggc:$TOMCAT_HOME/logs/gc.log \
       -Xms$INITIAL_HEAP \
       -Xmx$MAX_HEAP \
       -Xmn$YOUNG_GENERATION \
       -Xss512k \
       -Dactors.corePoolSize=100 \
       -Dcom.amazonaws.services.s3.disableGetObjectMD5Validation=true \
       -Djava.net.preferIPv4Stack=true \
       -Dlog4j.configurationFile=$TOMCAT_HOME/conf/log4j.xml \
       -Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true \
    "
else
    JAVA_OPTS=""
fi
```
