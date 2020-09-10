### Example persons1ds: kafka-hana-connect using strimzi kafka images with docker-compose

This example is a distributed version of example persons1 using strimizi kafka docker images.

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker, Docker-Compose

#### Running

This description assumes Docker and Docker-Compose are available on local machine.

##### Steps 1: Build Docker image for kafka-connect-hana

First, run `make get_libs` to place the required jar files into directory `target`.

```
$ make get_libs
Getting jar files into target ...
$
$ ls target 
kafka-connect-hana-1.0-SNAPSHOT.jar  ngdbc-2.5.49.jar
$
```

Next, run `make build_docker` to build the Docker image using strimzi's Kafka image and add the jar files to its plugins directory.

```
$ make docker_build
Building docker image ...
docker build . -t strimzi-connector-hana-min
Sending build context to Docker daemon  1.423MB
Step 1/5 : FROM strimzi/kafka:0.19.0-kafka-2.4.1
 ---> a0be3ed644fc
Step 2/5 : USER root:root
 ---> Using cache
 ---> e651c111cfd0
Step 3/5 : RUN mkdir -p /opt/kafka/plugins/kafka-connect-hana
 ---> Using cache
 ---> b5e5ec331af9
Step 4/5 : COPY ./target/ /opt/kafka/plugins/kafka-connect-hana/
 ---> Using cache
 ---> d04b23041483
Step 5/5 : USER 1001
 ---> Using cache
 ---> 44fb0e9e5347
Successfully built 44fb0e9e5347
Successfully tagged strimzi-connector-hana-min:latest
$
```

##### Step 2: Prepare the connector configuration files

We use connect-distributed.properties stored in directory custom-config for configuring Kafka-Connect. For configuring connectors, prepare the json version of the connector configuration files connect-hana-source-1.json and connect-hana-sink-1.json.

##### Step 3: Prepare the source table (Follow Step 4 of example persons1)

##### Step 4: Starting Zookeeper, Kafka, Kafka-Coonnect

The docker-compose.yaml file defines zookeeper, kafka, and connect services. It is noted that Kafka broker uses its advertised host set to `host.docker.internal:9092` assumeing this host name is resolvable from the containers and at the host. This allows Kafka broker to be accessed from the container of Kafka-Connect and from the host for inspection.

Run `docker-compose up` to start the containers.

```
$ docker-compose up
Creating network "persons1ds_default" with the default driver
Creating persons1ds_zookeeper_1 ... done
Creating persons1ds_kafka_1     ... done
Creating persons1ds_connect_1   ... done
Attaching to persons1ds_zookeeper_1, persons1ds_kafka_1, persons1ds_connect_1
...
```

After starting the Docker containers using docker-compose, we can verify whether Kafka Connect is running using curl.

```
$ curl -i http://localhost:8083/
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:00:11 GMT
Content-Type: application/json
Content-Length: 91
Server: Jetty(9.4.20.v20190813)

{"version":"2.4.1","commit":"c57222ae8cd7866b","kafka_cluster_id":"wdrDgSAFSbKpWGYm-q0PuQ"}
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:00:39 GMT
Content-Type: application/json
Content-Length: 2
Server: Jetty(9.4.20.v20190813)

[]
$
```

The above result shows that Kafka Connect using Kafka 2.4.1 is running and there is no connector deployed.

We prepare for the connector json files using the json files `connect-hana-source-1.json` and `connect-hana-sink-1.json` which are the json representation of the property files created for example persons1.

Finally, we deploy the connectors by posting the connector configuration json files to the Kafka Connect's API. Assuming, these json files are already prepared in step 3, use curl to post these files.

```
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-source-1.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Wed, 09 Sep 2020 22:10:34 GMT
Location: http://localhost:8083/connectors/test-topic-1-source
Content-Type: application/json
Content-Length: 399
Server: Jetty(9.4.20.v20190813)

{"name":"test-topic-1-source","config":{"connector.class":"com.sap.kafka.connect.source.hana.HANASourceConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-sink-1.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Wed, 09 Sep 2020 22:11:22 GMT
Location: http://localhost:8083/connectors/test-topic-1-sink
Content-Type: application/json
Content-Length: 414
Server: Jetty(9.4.20.v20190813)

{"name":"test-topic-1-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:11:54 GMT
Content-Type: application/json
Content-Length: 43
Server: Jetty(9.4.20.v20190813)

["test-topic-1-source","test-topic-1-sink"]
```

The above result shows that the connectors are deployed.


##### Step 5: Verifying the result (Follow Step 6 of example persions1 and/or persons2)

It is noted that this scenario builds the Docker image without schema registry usage and runs Kafka Connect in the distributed mode. Additional connectors can be deployed to this Kafka Connect instance which use the same distributed-connect.properties configuration.


##### Step 6: Shut down

Use `docker-compose down` to shutdown the containers.

```
$ docker-compose down
Stopping persons1ds_connect_1   ... done
Stopping persons1ds_kafka_1     ... done
Stopping persons1ds_zookeeper_1 ... done
Removing persons1ds_connect_1   ... done
Removing persons1ds_kafka_1     ... done
Removing persons1ds_zookeeper_1 ... done
Removing network persons1ds_default
$
```
