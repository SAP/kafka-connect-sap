### Example persons1db: kafka-hana-connect using debezium kafka images with docker

This example is a distributed version of example persons1 using debezium kafka-connect docker images.

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker

#### Running

This description assumes Docker and Docker-Compose are available on local machine. 

##### Step 1: Start Zookeeper and Kafka

First, we start both Zookeeper and Kafka using debezium zookeeper and kafka images. If you are not familiar with debezium, you can find more information at Debezim tutorial https://debezium.io/documentation/reference/1.2/tutorial.html

Start Zookeeper using the following `docker run` command.

```
$ docker run -it --rm --name zookeeper -p 2181:2181 -p 2888:2888 -p 3888:3888 debezium/zookeeper:1.2
Starting up in standalone mode
ZooKeeper JMX enabled by default
Using config: /zookeeper/conf/zoo.cfg
2020-09-09 22:42:33,018 - INFO  [main:QuorumPeerConfig@135] - Reading configuration from: /zookeeper/conf/zoo.cfg
2020-09-09 22:42:33,031 - INFO  [main:QuorumPeerConfig@387] - clientPortAddress is 0.0.0.0:2181
...
```

Start Kafka using the following `docker run` command.

```
$ docker run -it --rm --name kafka -p 9092:9092 --link zookeeper:zookeeper debezium/kafka:1.2
WARNING: Using default BROKER_ID=1, which is valid only for non-clustered installations.
Using ZOOKEEPER_CONNECT=172.17.0.2:2181
Using KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://172.17.0.3:9092
2020-09-09 22:43:05,396 - INFO  [main:Log4jControllerRegistration$@31] - Registered kafka:type=kafka.Log4jController MBean
2020-09-09 22:43:05,934 - INFO  [main:X509Util@79] - Setting -D jdk.tls.rejectClientInitiatedRenegotiation=true to disable client-initiated TLS renegotiation
...
```

Before we start Kafka-Connect, we must prepare the Docker image that contains kafka-connector-hana.

##### Steps 2: Build Docker image for kafka-connector-hana

First, run `make get_libs` to place the required jar files into directory `target`.

```
$ make get_libs
Getting jar files into target ...
$
$ ls target 
guava-20.0.jar                       ngdbc-2.5.49.jar
kafka-connector-hana-1.0.0-SNAPSHOT.jar
$
```

Next, run `make build_docker` to build the Docker image using debezium's Kafka Connect image and add the jar files to its plugins directory.


```
$ make docker_build
Building docker image ...
docker build . -t debezium-connector-hana-min
Sending build context to Docker daemon  3.868MB
Step 1/5 : FROM debezium/connect:1.2
 ---> 66f074fce2f0
Step 2/5 : USER root:root
 ---> Using cache
 ---> 64a9079cfa93
Step 3/5 : RUN mkdir -p /kafka/connect/kafka-connector-hana
 ---> Using cache
 ---> 174c3e1fb6db
Step 4/5 : COPY ./target/ /kafka/connect/kafka-connector-hana/
 ---> Using cache
 ---> 8f300532bf25
Step 5/5 : USER 1001
 ---> Using cache
 ---> b38cc3546555
Successfully built b38cc3546555
Successfully tagged debezium-connector-hana-min:latest
$ 
```

##### Step 3: Prepare the connector configuration files (Follow Step 2 of [persons1ds example](../persons1ds).

##### Step 4: Prepare the source table (Follow Step 4 of example persons1)

##### Step 5: Starting Kafka-Connect

Start Kafka-Connect using the following `docker run` command.

```
$ docker run -it --rm --name connect -p 8083:8083 -e GROUP_ID=1 -e CONFIG_STORAGE_TOPIC=my_connect_configs -e OFFSET_STORAGE_TOPIC=my_connect_offsets -e STATUS_STORAGE_TOPIC=my_connect_statuses --link zookeeper:zookeeper --link kafka:kafka debezium-connector-hana-min:latest
Plugins are loaded from /kafka/connect
Using the following environment variables:
      GROUP_ID=1
      CONFIG_STORAGE_TOPIC=my_connect_configs
      OFFSET_STORAGE_TOPIC=my_connect_offsets
      STATUS_STORAGE_TOPIC=my_connect_statuses
      BOOTSTRAP_SERVERS=172.17.0.3:9092
      REST_HOST_NAME=172.17.0.4
...
```

After starting those Docker containers, we can verify whether Kafka-Connect is running using curl.

```
$ curl -i http://localhost:8083/
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:44:49 GMT
Content-Type: application/json
Content-Length: 91
Server: Jetty(9.4.24.v20191120)

{"version":"2.5.0","commit":"66563e712b0b9f84","kafka_cluster_id":"1NEvm9a4TW2t-f5Jkk4peg"}
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:45:35 GMT
Content-Type: application/json
Content-Length: 2
Server: Jetty(9.4.24.v20191120)

[]
$
```

The above result shows that Kafka Connect using Kafka 2.5.0 is running and there is no connector deployed.

We prepare for the connector json files using the json files `connect-hana-source-1.json` and `connect-hana-sink-1.json` which are the json representation of the configuration files created for example persons1.

Finally, we deploy the connectors by posting the connector configuration json files to the Kafka Connect's API. Assuming, these json files are already prepared in step 3, use curl to post these files.

```
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-source-1.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Wed, 09 Sep 2020 22:46:30 GMT
Location: http://localhost:8083/connectors/test-topic-1-source
Content-Type: application/json
Content-Length: 399
Server: Jetty(9.4.24.v20191120)

{"name":"test-topic-1-source","config":{"connector.class":"com.sap.kafka.connect.source.hana.HANASourceConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-sink-1.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Wed, 09 Sep 2020 22:46:39 GMT
Location: http://localhost:8083/connectors/test-topic-1-sink
Content-Type: application/json
Content-Length: 414
Server: Jetty(9.4.24.v20191120)

{"name":"test-topic-1-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:47:35 GMT
Content-Type: application/json
Content-Length: 43
Server: Jetty(9.4.24.v20191120)

["test-topic-1-source","test-topic-1-sink"]
$
```

The above result shows that the connectors are deployed.

##### Step 6: Verifying the result

You can use debezium's watch-topic utility to look at the topic.

```
$ docker run -it --rm --name watcher --link zookeeper:zookeeper --link kafka:kafka debezium/kafka:1.2 watch-topic -a -k test_topic_1                 
WARNING: Using default BROKER_ID=1, which is valid only for non-clustered installations.
Using ZOOKEEPER_CONNECT=172.17.0.2:2181
Using KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://172.17.0.5:9092
Using KAFKA_BROKER=172.17.0.3:9092
Contents of topic test_topic_1:
null	{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":1,"LASTNAME":"simpson","FIRSTNAME":"homer"}}
null	{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":2,"LASTNAME":"simpson","FIRSTNAME":"merge"}}
...
```

You can also start another kafka container an interactive bash shell to inspect the topic using kafka-consule-consumer.sh.

First, start the interactive bash shell with debezium kafka image

```
$ docker run -it --rm --link zookeeper:zookeeper --link kafka:kafka debezium/kafka:1.2 /bin/bash
WARNING: Using default BROKER_ID=1, which is valid only for non-clustered installations.
Using ZOOKEEPER_CONNECT=172.17.0.2:2181
Using KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://172.17.0.5:9092
[kafka@2f92f972f2b6 ~]$
```

In the interactive shell, use kafka-console-consumer.sh to connect kafka:9092 to inspect the topic.

```
[kafka@2f92f972f2b6 ~]$ bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic test_topic_1 --from-beginning
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":1,"LASTNAME":"simpson","FIRSTNAME":"homer"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":2,"LASTNAME":"simpson","FIRSTNAME":"merge"}}
...
```

It is noted that this scenario builds the Docker image without schema registry usage and runs Kafka Connect in the distributed mode. Additional connectors can be deployed to this Kafka Connect instance which use the same distributed-connect.properties configuration.

##### Step 7: Cleaning up

Use docker stop to terminate the containers.

```
$ docker stop watcher connect kafka zookeeper
watcher
connect
kafka
zookeeper
$ 
```
