### Example persons1ds: kafka-hana-connect using strimzi kafka images with docker-compose

This example is a distributed version of example [persons1](../persons1/README.md) using strimizi kafka docker images.

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker, Docker-Compose

#### Running

This description assumes Docker and Docker-Compose are available on local machine.

##### Steps 1: Build Docker image for kafka-connector-hana

First, run `make get_libs` to place the required jar files into directory `target`.

```
$ make get_libs
Getting jar files into target ...
$
$ ls target 
ngdbc-2.10.14.jar
$
```

Download from https://github.com/SAP/kafka-connect-sap/releases the kafka-connector-hana_2.12-x.x.x.jar file that is suitable for your Kafka version and save it in `target` directory.

Next, run `make build_docker` to build the Docker image using strimzi's Kafka image and add the jar files to its plugins directory.

```
$ make docker_build
Building docker image ...
docker build . -t strimzi-connector-hana-min
Sending build context to Docker daemon  1.423MB
Step 1/5 : FROM strimzi/kafka:0.24.0-kafka-2.8.0
 ---> a0be3ed644fc
Step 2/5 : USER root:root
 ---> Using cache
 ---> e651c111cfd0
Step 3/5 : RUN mkdir -p /opt/kafka/plugins/kafka-connector-hana
 ---> Using cache
 ---> b5e5ec331af9
Step 4/5 : COPY ./target/ /opt/kafka/plugins/kafka-connector-hana/
 ---> Using cache
 ---> d04b23041483
Step 5/5 : USER 1001
 ---> Using cache
 ---> 44fb0e9e5347
Successfully built 44fb0e9e5347
Successfully tagged strimzi-connector-hana-min:latest
$
```

##### Step 2: Prepare the source table (Follow Step 4 of example [persons1](../persons1/README.md))

##### Step 3: Starting Zookeeper, Kafka, Kafka-Connect

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
$ curl http://localhost:8083/
{"version":"2.8.0","commit":"ebb1d6e21cc92130","kafka_cluster_id":"OpfSsMGsR3SozA9fA40a-Q"}
$
$ curl http://localhost:8083/connectors
[]
$
```

The above result shows that Kafka Connect using Kafka 2.4.1 is running and there is no connector deployed.

##### Step 4: Installing HANA connectors

We prepare for the connector json files using the json files `connect-hana-source-1.json` and `connect-hana-sink-1.json` which are the json representation of the property files created for example [persons1](../persons1/README.md). However, for this distributed example, the user and password values are placed in a separate configuration file `custom-config/hana-secrets.properties` and referenced in the conector json files. Adjust those values accordingly.

```
{
    "name": "test-topic-1-source",
    "config": {
        "connector.class": "com.sap.kafka.connect.source.hana.HANASourceConnector",
        "tasks.max": "1",
        "topics": "test_topic_1",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/opt/kafka/custom-config/hana-secrets.properties:connection1-user}",
        "connection.password": "${file:/opt/kafka/custom-config/hana-secrets.properties:connection1-password}",
        "test_topic_1.table.name": "\"<schemaname>\".\"PERSONS1\""
    }
}
```

```
{
    "name": "test-topic-1-sink",
    "config": {
        "connector.class": "com.sap.kafka.connect.source.hana.HANASourceConnector",
        "tasks.max": "1",
        "topics": "test_topic_1",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/opt/kafka/custom-config/hana-secrets.properties:connection1-user}",
        "connection.password": "${file:/opt/kafka/custom-config/hana-secrets.properties:connection1-password}",
        "auto.create": "true",
        "test_topic_1.table.name": "\"<schemaname>\".\"PERSONS1_RES\""
    }
}
```

Finally, we deploy the connectors by posting the connector configuration json files to the Kafka Connect's API using curl.

```
$ curl -X POST -H 'content-type:application/json' -d @connect-hana-source-1.json http://localhost:8083/connectors
{"name":"test-topic-1-source","config":{"connector.class":"com.sap.kafka.connect.source.hana.HANASourceConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-sink-1.json http://localhost:8083/connectors
{"name":"test-topic-1-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i http://localhost:8083/connectors
["test-topic-1-source","test-topic-1-sink"]
```

The above result shows that the connectors are deployed.


##### Step 5: Verifying the result (Follow Step 6 of example [persions1](../persions1/README.md))

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
